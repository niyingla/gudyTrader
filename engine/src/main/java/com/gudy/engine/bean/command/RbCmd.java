package com.gudy.engine.bean.command;

import com.gudy.engine.bean.orderbook.MatchEvent;
import lombok.Builder;
import lombok.ToString;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import thirdpart.hq.L1MarketData;
import thirdpart.order.CmdType;
import thirdpart.order.OrderDirection;
import thirdpart.order.OrderType;

import java.util.List;

@Builder
@ToString
public class RbCmd {

    /**
     * 时间戳
     */
    public long timestamp;

    /**
     * 会员id
     */
    public short mid;

    /**
     * 用户id
     */
    public long uid;

    /**
     * 指令类型
     */
    public CmdType command;

    /**
     * 股票代码
     */
    public int code;

    /**
     * 委托方向
     */
    public OrderDirection direction;

    /**
     * 价格
     */
    public long price;

    /**
     * 量
     */
    public long volume;

    /**
     * 委托编号
     */
    public long oid;

    /**
     * 委托类型
     */
    public OrderType orderType;


    // 保存撮合结果
    public List<MatchEvent> matchEventList;
    /**
     * 传递前面处理状态
     */
    // 前置风控 --> 撮合 --> 发布
    public CmdResultCode resultCode;

    // 保存行情
    public IntObjectHashMap<L1MarketData> marketDataMap;

}
