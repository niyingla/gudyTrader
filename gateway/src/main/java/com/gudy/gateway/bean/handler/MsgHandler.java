package com.gudy.gateway.bean.handler;

import com.gudy.gateway.bean.OrderCmdContainer;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import thirdpart.bean.CommonMsg;
import thirdpart.codec.IBodyCodec;
import thirdpart.order.OrderCmd;

@Log4j2
@AllArgsConstructor
public class MsgHandler implements IMsgHandler {

    private IBodyCodec bodyCodec;

    /**
     * 处理柜台过来的消息
     * @param msg
     */
    @Override
    public void onCounterData(CommonMsg msg) {
        OrderCmd orderCmd;

        try {
            //反序列化
            orderCmd = bodyCodec.deserialize(msg.getBody(), OrderCmd.class);
            log.info("recv cmd: {}",orderCmd);
//            log.debug("recv cmd: {}",orderCmd);
//            if(log.isDebugEnabled()){
//
//            }
            //写入委托订单缓存队列
            if(!OrderCmdContainer.getInstance().cache(orderCmd)){
                log.error("gateway queue insert fail,queue length:{},order:{}", OrderCmdContainer.getInstance().size(), orderCmd);
            }
        } catch (Exception e) {
            log.error("decode order cmd error", e);
        }
    }
}
