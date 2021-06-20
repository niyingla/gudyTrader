package thirdpart.hq;

import lombok.Builder;
import thirdpart.order.OrderStatus;

import java.io.Serializable;

@Builder
public class MatchData implements Serializable {

    /**
     * 时间戳
     */
    public long timestamp;

    /**
     * 会员id
     */
    public short mid;

    /**
     * 订单id
     */
    public long oid;

    /**
     * 订单状态
     */
    public OrderStatus status;

    public long tid;

    /**
     * 撤单数量 成交数量
     */
    public long volume;

    /**
     * 价格
     */
    public long price;

}
