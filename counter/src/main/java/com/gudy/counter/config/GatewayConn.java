package com.gudy.counter.config;

import com.alipay.remoting.exception.CodecException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import thirdpart.bean.CommonMsg;
import thirdpart.order.OrderCmd;
import thirdpart.tcp.TcpDirectSender;
import thirdpart.uuid.GudyUuid;

import javax.annotation.PostConstruct;

import static thirdpart.bean.MsgConstants.COUNTER_NEW_ORDER;
import static thirdpart.bean.MsgConstants.NORMAL;

@Log4j2
@Configuration
public class GatewayConn {
    /**
     * 网关链接工具类 柜台 -》 网关 -》 排队机 -》
     */

    @Autowired
    private CounterConfig config;

    private TcpDirectSender directSender;

    @PostConstruct
    private void init(){
        //初始化 tcp目标发送工具 （发送到网关 ）
        directSender = new TcpDirectSender(config.getSendIp(),config.getSendPort(),config.getVertx());
        directSender.startup();
    }

    /**
     * 发送订单
     * @param orderCmd
     */
    public void sendOrder(OrderCmd orderCmd){
        byte[] data = null;
        try {
            data = config.getBodyCodec().serialize(orderCmd);
        }catch (Exception e){
            log.error("encode error for ordercmd:{}",orderCmd,e);
            return;
        }

        CommonMsg msg  = new CommonMsg();
        msg.setBodyLength(data.length);
        msg.setChecksum(config.getCs().getChecksum(data));
        msg.setMsgSrc(config.getId());
        msg.setMsgDst(config.getGatewayId());
        msg.setMsgType(COUNTER_NEW_ORDER);
        msg.setStatus(NORMAL );
        msg.setMsgNo(GudyUuid.getInstance().getUUID());
        //序列胡后的订单内容
        msg.setBody(data);
        //发送序列化后的消息
        directSender.send(config.getMsgCodec().encodeToBuffer(msg));
    }

}
