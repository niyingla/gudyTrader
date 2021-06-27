package com.gudy.counter.bean;

import com.alipay.remoting.exception.CodecException;
import com.google.common.collect.ImmutableMap;
import com.gudy.counter.config.CounterConfig;
import com.gudy.counter.util.DbUtil;
import com.gudy.counter.util.IDConverter;
import com.gudy.counter.util.JsonUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import thirdpart.hq.L1MarketData;
import thirdpart.hq.MatchData;
import thirdpart.order.OrderCmd;
import thirdpart.order.OrderDirection;
import thirdpart.order.OrderStatus;

import javax.annotation.PostConstruct;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.gudy.counter.bean.MqttBusConsumer.INNER_MATCH_DATA_ADDR;
import static com.gudy.counter.config.WebSocketConfig.ORDER_NOTIFY_ADDR_PREFIX;
import static com.gudy.counter.config.WebSocketConfig.TRADE_NOTIFY_ADDR_PREFIX;
import static thirdpart.order.OrderDirection.BUY;
import static thirdpart.order.OrderDirection.SELL;
import static thirdpart.order.OrderStatus.*;

@Log4j2
@Component
public class MatchDataConsumer {

    public static final String ORDER_DATA_CACHE_ADDR = "order_data_cache_addr";


    @Autowired
    private CounterConfig config;

    //下单时缓存订单信息的集合
    //<key 委托编号，value OrderCmd>
    private LongObjectHashMap<OrderCmd> oidOrderMap = new LongObjectHashMap<>();


    @PostConstruct
    private void init() {
        EventBus eventBus = config.getVertx().eventBus();

        //接收委托缓存(从终端来)
        eventBus.consumer(ORDER_DATA_CACHE_ADDR)
                .handler(buffer -> {
                    Buffer body = (Buffer) buffer.body();
                    try {
                        OrderCmd om = config.getBodyCodec().deserialize(body.getBytes(), OrderCmd.class);
                        log.info("cache order:{}", om);
                        oidOrderMap.put(om.oid, om);
                    } catch (Exception e) {
                        log.error(e);
                    }
                });


        //接受总线撮合数据请求 (修改数据库状态)
        eventBus.consumer(INNER_MATCH_DATA_ADDR)
                .handler(buffer -> {
                    //数据长度判断
                    Buffer body = (Buffer) buffer.body();
                    if (body.length() == 0) {
                        return;
                    }
                    MatchData[] matchDataArr = null;
                    try {
                        matchDataArr = config.getBodyCodec().deserialize(body.getBytes(), MatchData[].class);
                    } catch (Exception e) {
                        log.error(e);
                    }

                    if (ArrayUtils.isEmpty(matchDataArr)) {
                        return;
                    }

                    //按照oid进行分类 w委托单id进行分类
                    Map<Long, List<MatchData>> collect = Arrays.asList(matchDataArr)
                            .stream().collect(Collectors.groupingBy(t -> t.oid));

                    //循环订单列表
                    for (Map.Entry<Long, List<MatchData>> entry : collect.entrySet()) {

                        if (CollectionUtils.isEmpty(entry.getValue())) {
                            continue;
                        }

                        //拆分获取柜台内部委托编号
                        long oid = entry.getKey();
                        //获取柜台内部编号
                        int counterOId = IDConverter.seperateLong2Int(oid)[1];//高位 柜台  低位 内部ID
                        //回调 修改数据库和委托终端
                        updateAndNotify(counterOId, entry.getValue(), oidOrderMap.get(oid));
                    }
                });
    }


    /**
     * 修改数据库和委托终端
     * @param counterOId 柜台内部委托id
     * @param value 撮合数据
     * @param orderCmd 原始下单信息
     */
    private void updateAndNotify(int counterOId, List<MatchData> value, OrderCmd orderCmd) {
        if (CollectionUtils.isEmpty(value)) {
            return;
        }
        //成交
        for (MatchData md : value) {
            OrderStatus status = md.status;
            //只处理 成交和委托数据

            //成交
            if (status == TRADE_ED || status == PART_TRADE) {
                //插入成交
                DbUtil.saveTrade(counterOId, md, orderCmd);
                //持仓 资金 多退少补
                if (orderCmd.direction == BUY) {
                    //B 13 30股    10 10股
                    //判断是否有价格差
                    if (orderCmd.price > md.price) {
                        //变动资金 委托时的价格和成交价格进行求差 （委托价 小于等于 成交价）得出最终变动资金
                        DbUtil.addBalance(orderCmd.uid, (orderCmd.price - md.price) * md.volume);
                    }
                    //买单 增加持仓
                    DbUtil.addPosi(orderCmd.uid, orderCmd.code, md.volume, md.price);
                } else if (orderCmd.direction == SELL) {
                    //卖单 加钱
                    DbUtil.addBalance(orderCmd.uid, md.price * md.volume);
                } else {
                    log.error("wrong direction[{}]", orderCmd.direction);
                }

                //通知客户端撮合变动 然后客户端会来主动拉取
                config.getVertx().eventBus().publish(
                        TRADE_NOTIFY_ADDR_PREFIX + orderCmd.uid,
                        JsonUtil.toJson(
                                ImmutableMap.of("code", orderCmd.code,
                                        "direction", orderCmd.direction,
                                        "volume", md.volume)
                        )
                );

            }
        }


        // 委托变动
        //根据最后一笔Match处理委托 （委托依序处理）
        MatchData finalMatchData = value.get(value.size() - 1);
        //判断最后一笔状态
        OrderStatus finalOrderStatus = finalMatchData.status;
        //修改订单状态
        DbUtil.updateOrder(orderCmd.uid, counterOId, finalOrderStatus);
        if (finalOrderStatus == CANCEL_ED || finalOrderStatus == PART_CANCEL) {
            //清空缓存
            oidOrderMap.remove(orderCmd.oid);
            if (orderCmd.direction == BUY) {
                //撤买 （加回部分资金）
                DbUtil.addBalance(orderCmd.uid, -(orderCmd.price * finalMatchData.volume));
            } else if (orderCmd.direction == SELL) {
                //增加持仓  撤卖单
                DbUtil.addPosi(orderCmd.uid, orderCmd.code, -finalMatchData.volume, orderCmd.price);
            } else {
                log.error("wrong direction[{}]", orderCmd.direction);
            }
        }

        //通知委托终端 然后客户端会来主动拉取
        config.getVertx().eventBus().publish(
                ORDER_NOTIFY_ADDR_PREFIX + orderCmd.uid, ""
        );

    }
}
