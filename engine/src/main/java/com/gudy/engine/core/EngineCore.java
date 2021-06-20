package com.gudy.engine.core;

import com.gudy.engine.bean.RbCmdFactory;
import com.gudy.engine.bean.command.RbCmd;
import com.gudy.engine.handler.BaseHandler;
import com.gudy.engine.handler.exception.DisruptorExceptionHandler;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
import thirdpart.order.CmdType;
import thirdpart.order.OrderCmd;

import java.util.Timer;
import java.util.TimerTask;

import static com.gudy.engine.handler.pub.L1PubHandler.HQ_PUB_RATE;

@Log4j2
public class EngineCore {
    /**
     * 订单 600w/s
     * 环形缓冲区 本质固定长度数组 每个数据就是一个event 数量初始化已经指定
     * 1 定长，event 预加载
     * 新增消息就是给 event 成员变量 加了个引用，写入过程有序递增。
     * 2 单线程 cas写 来避免锁使用 （建议每个系统 Disruptor 个数不要超过核数）
     * 3 缓存补齐
     * （一级 1ns 二级缓存） threa 独享 三级缓存 共享 公共缓存 70ns
     * 缓存行 单个缓存 64字节 ring buffer会让单个事件沾满 （防止读到其他部分数据 因为
     * cpu 一次读取可能会读取多个 但是数据高度变化 如果使用时 下一个事件被更新 又要重新加载 这样杜绝了 这种情况）
     * 4 内存栅栏 volatile 防止乱序
     * 比较生产游标和消费游标（游标 volatile 修饰） 防止读取越界
     */

    private final Disruptor<RbCmd> disruptor;

    private static final int RING_BUFFER_SIZE = 1024;

    @Getter
    private final EngineApi api;

    public EngineCore(
            @NonNull final BaseHandler riskHandler,
            @NonNull final BaseHandler matchHandler,
            @NonNull final BaseHandler pubHandler
    ) {
        //创建环形下单队列
        this.disruptor = new Disruptor<>(
                //事件工厂类
                new RbCmdFactory(),
                //队列大小 2n次幂 会进行位运算
                RING_BUFFER_SIZE,
                //亲和线程池 线程亲和性能够强制使你的应用线程运行在特定的一个或多个cpu上
                new AffinityThreadFactory("aft_engine_core", AffinityStrategies.SAME_CORE),
                //一个生产者
                ProducerType.SINGLE,
                //等待策略 阻塞队列
                new BlockingWaitStrategy()
        );

        //
        this.api = new EngineApi(disruptor.getRingBuffer());

        //1.全局异常处理器
        final DisruptorExceptionHandler<RbCmd> exceptionHandler = new DisruptorExceptionHandler<>("main", (ex, seq) -> {
                    log.error("exception thrown on seq={}", seq, ex);
                });

        disruptor.setDefaultExceptionHandler(exceptionHandler);

        //2. 设置处理事件handle 并制定顺序
        // 前置风控 --> 撮合 --> 发布数据
        disruptor
                //风控
                .handleEventsWith(riskHandler)
                //撮合
                .then(matchHandler)
                //发布行情
                .then(pubHandler);

        //3.启动
        disruptor.start();
        log.info("match engine start");


        //4.定时发布行情生产任务
        new Timer().schedule(new HqPubTask(), 5000, HQ_PUB_RATE);

    }


    /**
     * 行情定时任务
     */
    private class HqPubTask extends TimerTask {

        @Override
        public void run() {
            api.submitCommand(OrderCmd.builder().type(CmdType.HQ_PUB).build());
        }
    }


}
