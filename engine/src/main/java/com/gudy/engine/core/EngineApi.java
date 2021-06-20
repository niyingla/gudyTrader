package com.gudy.engine.core;

import com.gudy.engine.bean.command.CmdResultCode;
import com.gudy.engine.bean.command.RbCmd;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import thirdpart.order.CmdType;
import thirdpart.order.OrderCmd;

@Log4j2
@AllArgsConstructor
public class EngineApi {

    /**
     * 请求动作环形队列
     */
    private final RingBuffer<RbCmd> ringBuffer;


    /**
     * 提交请求
     * @param cmd
     */
    public void submitCommand(OrderCmd cmd) {
        //根据请求类型放入队列
        switch (cmd.type) {
            //行情
            case HQ_PUB:
                ringBuffer.publishEvent(HQ_PUB_TRANSLATOR, cmd);
                break;
            //下单
            case NEW_ORDER:
                ringBuffer.publishEvent(NEW_ORDER_TRANSLATOR, cmd);
                break;
            //撤单
            case CANCEL_ORDER:
                ringBuffer.publishEvent(CANCEL_ORDER_TRANSLATOR, cmd);
                break;
            default:
                throw new IllegalArgumentException("Unsupported cmdType : " + cmd.getClass().getSimpleName());
        }
    }


    /**
     * 委托trans
     */
    private static final EventTranslatorOneArg<RbCmd, OrderCmd> NEW_ORDER_TRANSLATOR = (rbCmd, seq, newOrder) -> {
        rbCmd.command = CmdType.NEW_ORDER;
        rbCmd.timestamp = newOrder.timestamp;
        rbCmd.mid = newOrder.mid;
        rbCmd.uid = newOrder.uid;
        rbCmd.code = newOrder.code;
        rbCmd.direction = newOrder.direction;
        rbCmd.price = newOrder.price;
        rbCmd.volume = newOrder.volume;
        rbCmd.orderType = newOrder.orderType;
        rbCmd.oid = newOrder.oid;
        rbCmd.resultCode = CmdResultCode.SUCCESS;
    };

    /**
     * 撤单trans
     */
    private static final EventTranslatorOneArg<RbCmd, OrderCmd> CANCEL_ORDER_TRANSLATOR = (rbCmd, seq, cancelOrder) -> {
        rbCmd.command = CmdType.CANCEL_ORDER;
        rbCmd.timestamp = cancelOrder.timestamp;
        rbCmd.mid = cancelOrder.mid;
        rbCmd.uid = cancelOrder.uid;
        rbCmd.code = cancelOrder.code;
        rbCmd.oid = cancelOrder.oid;
        rbCmd.resultCode = CmdResultCode.SUCCESS;
    };

    /**
     * 行情发送
     */
    private static final EventTranslatorOneArg<RbCmd, OrderCmd> HQ_PUB_TRANSLATOR = (rbCmd, seq, hqPub) -> {
        rbCmd.command = CmdType.HQ_PUB;
        rbCmd.resultCode = CmdResultCode.SUCCESS;
    };


}
