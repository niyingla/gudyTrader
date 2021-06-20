package com.gudy.counter.service;

import com.gudy.counter.bean.res.OrderInfo;
import com.gudy.counter.bean.res.PosiInfo;
import com.gudy.counter.bean.res.TradeInfo;

import java.util.List;

public interface IOrderService {

    //查资金
    Long getBalance(long uid);

    //查持仓
    List<PosiInfo> getPostList(long uid);

    //查委托
    List<OrderInfo> getOrderList(long uid);

    //查成交
    List<TradeInfo> getTradeList(long uid);

    //发送委托
    boolean sendOrder(long uid, short type, long timestamp,
                      int code, byte direction, long price, long volume,
                      byte ordertype);


    boolean cancelOrder(int uid, int counteroid, int code);


}
