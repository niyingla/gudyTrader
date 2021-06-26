package com.gudy.counter.bean;

import com.google.common.collect.Maps;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import thirdpart.bean.CommonMsg;
import thirdpart.checksum.ICheckSum;
import thirdpart.codec.IMsgCodec;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static thirdpart.bean.MsgConstants.MATCH_HQ_DATA;
import static thirdpart.bean.MsgConstants.MATCH_ORDER_DATA;

@RequiredArgsConstructor
@Log4j2
public class MqttBusConsumer {

    /**
     * 总线ip
     */
    @NonNull
    private String busIp;

    /**
     * 总线端口
     */
    @NonNull
    private int busPort;

    /**
     * 数柜台id
     */
    @NonNull
    private String recvAddr;

    @NonNull
    private IMsgCodec msgCodec;

    @NonNull
    private ICheckSum cs;

    @NonNull
    private Vertx vertx;

    public void startup() {
        mqttConnect(vertx, busPort, busIp);
    }

    /**
     * 行情地址
     */
    private final static String HQ_ADDR = "-1";

    public static final String INNER_MARKET_DATA_CACHE_ADDR = "l1_market_data_cache_addr";

    public static final String INNER_MATCH_DATA_ADDR = "match_data_addr";


    /**
     * 链接总线 （接受行情、委托信息）
     * @param vertx
     * @param busPort
     * @param busIp
     */
    private void mqttConnect(Vertx vertx, int busPort, String busIp) {
        MqttClient mqttClient = MqttClient.create(vertx);
        //链接并设置异步处理hand
        mqttClient.connect(busPort, busIp, res -> {
            if (res.succeeded()) {
                log.info("connect mqtt bus succeed");
                //订阅地址信息 map key 地址 value 次数 指定以下两个地址
                Map<String, Integer> topoic = Maps.newHashMap();
                //接受地址 柜台id
                topoic.put(recvAddr, MqttQoS.AT_LEAST_ONCE.value());
                //行情固定地址
                topoic.put(HQ_ADDR, MqttQoS.AT_LEAST_ONCE.value());
                //订阅消息
                mqttClient.subscribe(topoic)
                        .publishHandler(h -> {
                            //获取消息内容
                            CommonMsg msg = msgCodec.decodeFromBuffer(h.payload());
                            //校验数据
                            if (msg.getChecksum() != (cs.getChecksum(msg.getBody()))) {
                                return;
                            }

                            byte[] body = msg.getBody();

                            if (ArrayUtils.isNotEmpty(body)) {
                                short msgType = msg.getMsgType();
                                // 帮助去重，为了演示方便 不做校验
//                                long msgNo = msg.getMsgNo();
                                //撮合数据类型
                                if (msgType == MATCH_ORDER_DATA) {
                                    //放到对应总线 总线会有处理器消息 解耦 MatchDataConsumer
                                    vertx.eventBus().send(INNER_MATCH_DATA_ADDR, Buffer.buffer(body));
                                //行情数据类型
                                } else if (msgType == MATCH_HQ_DATA) {
                                    //放到对应总线 总线会有处理器消息 解耦 MarketDataConsumer
                                    vertx.eventBus().send(INNER_MARKET_DATA_CACHE_ADDR, Buffer.buffer(body));
                                } else {
                                    log.error("recv unknown msgType:{}", msg);
                                }
                            }
                        });
            } else {
                log.error("connect mqtt bus failed");
            }
        });

        mqttClient.closeHandler(h -> {
            try {
                //重连间隔
                TimeUnit.SECONDS.sleep(5);
            } catch (Exception e) {
                log.error(e);
            }
            //发起重连
            mqttConnect(vertx, busPort, busIp);
        });
    }

}
