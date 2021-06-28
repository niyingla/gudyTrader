package com.gudy.engine.bean.orderbook;

import com.gudy.engine.bean.command.RbCmd;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.qpid.proton.reactor.impl.IO;
import thirdpart.order.OrderStatus;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Log4j2
@ToString
public class GOrderBucketImpl implements IOrderBucket {

    /**
     * 同等价格集合
     */

    //1.价格
    @Getter
    @Setter
    private long price;

    //2.量
    @Getter
    private long totalVolume = 0;

    //3.委托列表 list行不通 增删快
    private final LinkedHashMap<Long, Order> entries = new LinkedHashMap<>();


    @Override
    public void put(Order order) {
        //加入委托集合
        entries.put(order.getOid(), order);
        //剩余委托量
        totalVolume += order.getVolume() - order.getTvolume();
    }

    @Override
    public Order remove(long oid) {
        //防止重复执行删除订单的请求
        Order order = entries.get(oid);
        if (order == null) {
            return null;
        }
        //删除委托
        entries.remove(oid);

        //加回剩余量
        totalVolume -= order.getVolume() - order.getTvolume();

        return order;
    }

    /**
     * 撮合匹配
     * @param volumeLeft 需要匹配数量
     * @param triggerCmd 委托单信息
     * @param removeOrderCallback 回调函数
     * @return
     */
    @Override
    public long match(long volumeLeft, RbCmd triggerCmd, Consumer<Order> removeOrderCallback) {
        // S 46 --> 5 10 24
        // S 45 --> 11 20 10 20
        // B 45 100

        Iterator<Map.Entry<Long, Order>> iterator = entries.entrySet().iterator();

        //累计匹配量
        long volumeMatch = 0;

        //遍历委托列表 并且还要剩余委托量
        while (iterator.hasNext() && volumeLeft > 0) {
            Map.Entry<Long, Order> next = iterator.next();
            Order order = next.getValue();
            //计算order可以吃多少量
            //这笔委托可以吃多少量 是 自己委托量 和 剩余委托 取最小值
            long traded = Math.min(volumeLeft, order.getVolume() - order.getTvolume());
            //累加到累计匹配量
            volumeMatch += traded;

            //1.order自身的量 2.volumeleft 3.bucket总委托量
            order.setTvolume(order.getTvolume() + traded);
            //需要匹配数量 减去本次可以吃量
            volumeLeft -= traded;
            ///总委托数量 减去本次可以吃量
            totalVolume -= traded;

            //生成事件 双向是否都完成委托
            boolean fullMatch = order.getVolume() == order.getTvolume();
            genMatchEvent(order, triggerCmd, fullMatch, volumeLeft == 0, traded);

            //如果完全匹配
            if (fullMatch) {
                //移除委托信息
                removeOrderCallback.accept(order);
                iterator.remove();
            }
        }

        //返回撮合量
        return volumeMatch;
    }


    /**
     * 生产撮合成功事件
     * @param order 订单信息
     * @param cmd
     * @param fullMatch 是否完成匹配
     * @param cmdFullMatch
     * @param traded 成交量
     */
    private void genMatchEvent(final Order order, final RbCmd cmd, boolean fullMatch, boolean cmdFullMatch, long traded) {

        long now = System.currentTimeMillis();

        //生成撮合单号
        long tid = IOrderBucket.tidGen.getAndIncrement();

        //两个MatchEvent
        MatchEvent bidEvent = new MatchEvent();
        bidEvent.timestamp = now;
        bidEvent.mid = cmd.mid;
        bidEvent.oid = cmd.oid;
        bidEvent.status = cmdFullMatch ? OrderStatus.TRADE_ED : OrderStatus.PART_TRADE;
        bidEvent.tid = tid;
        bidEvent.volume = traded;
        bidEvent.price = order.getPrice();
        //写入成交数组
        cmd.matchEventList.add(bidEvent);


        MatchEvent ofrEvent = new MatchEvent();
        ofrEvent.timestamp = now;
        ofrEvent.mid = order.getMid();
        ofrEvent.oid = order.getOid();
        ofrEvent.status = fullMatch ? OrderStatus.TRADE_ED : OrderStatus.PART_TRADE;
        ofrEvent.tid = tid;
        ofrEvent.volume = traded;
        ofrEvent.price = order.getPrice();
        //写入成交数组
        cmd.matchEventList.add(ofrEvent);

    }


    /**
     * 判断时是否是一个orderBucket
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        GOrderBucketImpl that = (GOrderBucketImpl) o;

        return new EqualsBuilder()
                .append(price, that.price)
                .append(entries, that.entries)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(price)
                .append(entries)
                .toHashCode();
    }
}
