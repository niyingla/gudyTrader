package com.gudy.engine.handler.risk;

import com.gudy.engine.bean.command.CmdResultCode;
import com.gudy.engine.bean.command.RbCmd;
import com.gudy.engine.handler.BaseHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import thirdpart.order.CmdType;

@Log4j2
@RequiredArgsConstructor
public class ExistRiskHandler extends BaseHandler {
    /**
     * 前置风控处理器
     */


    /**
     * 用户id集合
     */
    @NonNull
    private MutableLongSet uidSet;

    /**
     * 股票代码集合
     */
    @NonNull
    private MutableIntSet codeSet;

    //发布行情Event
    //新委托event
    //撤单event
    //权限控制，系统关机。。。
    @Override
    public void onEvent(RbCmd cmd, long sequence, boolean endOfBatch) throws Exception {

        //系统内部的指令(如 定时行情生成指令)，前置风控模块直接忽略
        if (cmd.command == CmdType.HQ_PUB) {
            return;
        }


        if (cmd.command == CmdType.NEW_ORDER || cmd.command == CmdType.CANCEL_ORDER) {
            //1.用户是否存在
            if (!uidSet.contains(cmd.uid)) {
                log.error("illegal uid[{}] exist", cmd.uid);
                cmd.resultCode = CmdResultCode.RISK_INVALID_USER;
                return;
            }

            //2.股票代码是否合法
            if (!codeSet.contains(cmd.code)) {
                log.error("illegal ocde[{}] exist", cmd.code);
                cmd.resultCode = CmdResultCode.RISK_INVALID_CODE;
                return;
            }
        }
    }
}
