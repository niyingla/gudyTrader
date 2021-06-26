package com.gudy.engine.handler.match;

import com.gudy.engine.bean.command.CmdResultCode;
import com.gudy.engine.bean.command.RbCmd;
import com.gudy.engine.bean.orderbook.IOrderBook;
import com.gudy.engine.handler.BaseHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

@RequiredArgsConstructor
public class StockMatchHandler extends BaseHandler {
    /**
     * 撮合
     */

    @NonNull
    private final IntObjectHashMap<IOrderBook> orderBookMap;

    /**
     * 撮合事件
     * @param cmd
     * @param sequence
     * @param endOfBatch
     */
    @Override
    public void onEvent(RbCmd cmd, long sequence, boolean endOfBatch) {
        //风控未通过
        if (cmd.resultCode.getCode() < 0) {
            return;
        }

        //进行撮合
        cmd.resultCode = processCmd(cmd);

    }

    /**
     * 进行撮合
     * @param cmd
     * @return
     */
    private CmdResultCode  processCmd(RbCmd cmd) {
        switch (cmd.command) {
            case NEW_ORDER:
                return orderBookMap.get(cmd.code).newOrder(cmd);
            case CANCEL_ORDER:
                return orderBookMap.get(cmd.code).cancelOrder(cmd);
            case HQ_PUB:
                orderBookMap.forEachKeyValue((code, orderBook) ->
                        cmd.marketDataMap.put(code, orderBook.getL1MarketDataSnapshot())
                );
            default:
                return CmdResultCode.SUCCESS;
        }
    }
}
