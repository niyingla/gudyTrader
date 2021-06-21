package com.gudy.counter.config;

import com.gudy.counter.bean.MqttBusConsumer;
import io.vertx.core.Vertx;
import lombok.Getter;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import thirdpart.checksum.ICheckSum;
import thirdpart.codec.IBodyCodec;
import thirdpart.codec.IMsgCodec;

import javax.annotation.PostConstruct;

@Getter
@Component
@Log4j2
public class CounterConfig {


    /////////////////////会员号////////////////////////////////
    @Value("${counter.id}")
    private short id;

    /////////////////////UUID 相关配置////////////////////////////////
    @Value("${counter.dataCenterId}")
    private long dataCenterId;

    @Value("${counter.workerId}")
    private long workerId;
/////////////////////////////////////////////////////

    /////////////////////和委托终端通讯 websocket配置////////////////////////////////
    @Value("${counter.pubport}")
    private int pubPort;

    ///////////////////总线配置//////////////////////

    @Value("${counter.subbusip}")
    private String subBusIp;

    @Value("${counter.subbusport}")
    private int subBusPort;

    /////////////////////网关配置////////////////////////////////
    @Value("${counter.sendip}")
    private String sendIp;

    @Value("${counter.sendport}")
    private int sendPort;

    @Value("${counter.gatewayid}")
    private short gatewayId;
/////////////////////////////////////////////////////

    private Vertx vertx = Vertx.vertx();

    /////////////////////编码相关配置////////////////////////////////
//    private ICheckSum cs = new ByteCheckSum();
//
//    private IBodyCodec bodyCodec = new BodyCodec();

    @Value("${counter.checksum}")
    private String checkSumClass;

    @Value("${counter.bodycodec}")
    private String bodyCodecClass;

    @Value("${counter.msgcodec}")
    private String msgCodecClass;


    private ICheckSum cs;

    private IBodyCodec bodyCodec;

    private IMsgCodec msgCodec;

    /**
     * 初始化方法 检查器、编码、解码 总线链接
     */
    @PostConstruct
    private void init() {
        Class<?> clz;

        try {
            clz = Class.forName(checkSumClass);
//            clz.newInstance();
            //实例化获取检查器
            cs = (ICheckSum) clz.getDeclaredConstructor().newInstance();

            //实例请求体序列化工具
            clz = Class.forName(bodyCodecClass);
            bodyCodec = (IBodyCodec) clz.getDeclaredConstructor().newInstance();

            //实例消息序列化工具
            clz = Class.forName(msgCodecClass);
            msgCodec = (IMsgCodec) clz.getDeclaredConstructor().newInstance();

        } catch (Exception e) {
            log.error("init config error ", e);
        }


        //初始化总线数据消费连接 接受行情、委托信息
        new MqttBusConsumer(subBusIp, subBusPort, String.valueOf(id), msgCodec, cs, vertx).startup();

    }
}
