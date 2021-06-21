package com.gudy.engine.bean;

import com.alipay.remoting.exception.CodecException;
import com.alipay.sofa.jraft.rhea.client.RheaKVStore;
import com.alipay.sofa.jraft.rhea.storage.KVEntry;
import com.alipay.sofa.jraft.util.Bits;
import com.google.common.collect.Lists;
import com.gudy.engine.core.EngineApi;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import thirdpart.bean.CmdPack;
import thirdpart.codec.IBodyCodec;
import thirdpart.order.OrderCmd;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

@Log4j2
public class CmdPacketQueue {

    private static CmdPacketQueue ourInstance = new CmdPacketQueue();

    private CmdPacketQueue() {
    }

    public static CmdPacketQueue getInstance() {
        return ourInstance;
    }

    ////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<CmdPack> recvCache = new LinkedBlockingDeque<>();

    public void cache(CmdPack pack) {
        recvCache.offer(pack);
    }

    ////////////////////////////////////////////////////////////////////////

    private RheaKVStore orderKVStore;

    private IBodyCodec codec;

    private EngineApi engineApi;


    /**
     * 初始化 下单包队列
     * @param orderKVStore
     * @param codec
     * @param engineApi
     */
    public void init(RheaKVStore orderKVStore, IBodyCodec codec, EngineApi engineApi) {
        this.orderKVStore = orderKVStore;
        this.codec = codec;
        this.engineApi = engineApi;

        new Thread(() -> {
            while (true) {
                try {
                    //取数据
                    CmdPack cmds = recvCache.poll(10, TimeUnit.SECONDS);
                    if (cmds != null) {
                        handle(cmds);
                    }
                } catch (Exception e) {
                    log.error("msg packet recvcache error,continue", e);
                }
            }
        }).start();
    }

    private long lastPackNo = -1;

    /**
     * 处理下单数据
     * @param cmdPack
     * @throws CodecException
     */
    private void handle(CmdPack cmdPack) throws CodecException {
        log.info("recv : {}", cmdPack);

        //NACK 异常包主动请求
        long packNo = cmdPack.getPackNo();
        //有序收包
        if (packNo == lastPackNo + 1) {
            if (CollectionUtils.isEmpty(cmdPack.getOrderCmds())) {
                return;
            }
            //循环提交请求
            for (OrderCmd cmd : cmdPack.getOrderCmds()) {
                engineApi.submitCommand(cmd);
            }
            //重复
        } else if (packNo <= lastPackNo) {
            //来自历史的重复的包 丢弃
            log.warn("recv duplicate packId : {}", packNo);
        } else {
            //跳号
            log.info("packNo lost from {} to {},begin query from sequencer", lastPackNo + 1, packNo);
            //请求数据
            byte[] firstKey = new byte[8];
            //取最后一个成功的下一个序号 作为缺失数据左端点
            Bits.putLong(firstKey, 0, lastPackNo + 1);

            byte[] lastKey = new byte[8];
            //丢失数据右端点 （不取当前点）
            Bits.putLong(lastKey, 0, packNo + 1);
            //捞取丢失区间
            final List<KVEntry> kvEntries = orderKVStore.bScan(firstKey, lastKey);
            //有数据
            if (CollectionUtils.isNotEmpty(kvEntries)) {
                List<CmdPack> collect = Lists.newArrayList();
                //不为空的数据加入 请求包集合
                for (KVEntry entry : kvEntries) {
                    byte[] value = entry.getValue();
                    if (ArrayUtils.isNotEmpty(value)) {
                        try {
                            collect.add(codec.deserialize(value, CmdPack.class));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                //包号排序
                collect.sort((o1, o2) -> (int) (o1.getPackNo() - o2.getPackNo()));
                //
                for (CmdPack pack : collect) {
                    //判断包中内容
                    if (CollectionUtils.isEmpty(pack.getOrderCmds())) {
                        continue;
                    }
                    //循环提交请求
                    for (OrderCmd cmd : pack.getOrderCmds()) {
                        engineApi.submitCommand(cmd);
                    }
                }
            }
            //排队机出错 导致出现了跳号
            lastPackNo = packNo;

        }

    }


}
