package com.gudy.engine.bean.orderbook;

import com.google.common.collect.Lists;
import com.gudy.engine.bean.command.CmdResultCode;
import com.gudy.engine.bean.command.RbCmd;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import thirdpart.hq.L1MarketData;
import thirdpart.order.OrderDirection;
import thirdpart.order.OrderStatus;

import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

@Log4j2
@RequiredArgsConstructor
public class GOrderBookImpl implements IOrderBook {

    /**
     * 每个股票都有自己的订单簿 这里定义股票代码
     */
    @NonNull
    private int code;

    //<价格,orderbucket>
    //买Bucket
    private final NavigableMap<Long, IOrderBucket> sellBuckets = new TreeMap<>();
    //卖Bucket （价格倒叙）
    private final NavigableMap<Long, IOrderBucket> buyBuckets = new TreeMap<>(Collections.reverseOrder());
    //委托缓存 key 订单号 value 订单
    private final LongObjectHashMap<Order> oidMap = new LongObjectHashMap<>();

    /**
     * 新增委托
     * @param cmd
     * @return
     */
    @Override
    public CmdResultCode newOrder(RbCmd cmd) {

        //1.判断重复
        if (oidMap.containsKey(cmd.oid)) {
            return CmdResultCode.DUPLICATE_ORDER_ID;
        }

        //2.生成新Order
        //2.1 预撮合
        // S 50 100  买单Buckets >=50 所有OrderBucket
        // B 40 200  卖单Buckets <=40 符合条件

        //根据订单方向获取对手单数组
        NavigableMap<Long, IOrderBucket> subMatchBuckets = (cmd.direction == OrderDirection.SELL ? buyBuckets : sellBuckets)
                //在方法调用返回此映射的键严格小于传入价格部分map
                .headMap(cmd.price, true);
        //预撮合 从对手下单桶匹配
        long tVolume = preMatch(cmd, subMatchBuckets);
        //撮合量 = 委托量
        if (tVolume == cmd.volume) {
            return CmdResultCode.SUCCESS;
        }

        //有生成撮合订单
        final Order order = Order.builder()
                .mid(cmd.mid)
                .uid(cmd.uid)
                .code(cmd.code)
                .direction(cmd.direction)
                .price(cmd.price)
                //下单量
                .volume(cmd.volume)
                //累计撮合量
                .tvolume(tVolume)
                .oid(cmd.oid)
                .timestamp(cmd.timestamp)
                .build();

        if (tVolume == 0) {
            //没有成交量
            genMatchEvent(cmd, OrderStatus.ORDER_ED);
        } else {
            //部分交易成功
            genMatchEvent(cmd, OrderStatus.PART_TRADE);
        }

        //3.加入orderBucket
        final IOrderBucket bucket = (cmd.direction == OrderDirection.SELL ? sellBuckets : buyBuckets)
                //没有就创建新的Bucket
                .computeIfAbsent(cmd.price, p -> {
                    final IOrderBucket b = IOrderBucket.create(IOrderBucket.OrderBucketImplType.GUDY);
                    b.setPrice(p);
                    return b;
                });
        //新增订单信息
        bucket.put(order);
        //加入订单集合
        oidMap.put(cmd.oid, order);

        return CmdResultCode.SUCCESS;
    }

    /**
     * 预撮合
     * @param cmd 当前下单信息
     * @param matchingBuckets 价格部分map
     * @return
     */
    private long preMatch(RbCmd cmd, NavigableMap<Long, IOrderBucket> matchingBuckets) {
        // 委托量已经撮合量
        int tVol = 0;
        //没有可以存在的
        if (matchingBuckets.size() == 0) {
            return tVol;
        }

        List<Long> emptyBuckets = Lists.newArrayList();
        //循环可撮合Bucket 价格优先 从最低 往上撮合
        for (IOrderBucket bucket : matchingBuckets.values()) {
            //撮合符合的bucket  累加到 委托量已经撮合量       剩余委托量 下单信息 回调函数
            tVol += bucket.match(cmd.volume - tVol, cmd, order -> oidMap.remove(order.getOid()));
            //累计委托量 = 0 委托耗尽了
            if (bucket.getTotalVolume() == 0) {
                //加入空列表
                emptyBuckets.add(bucket.getPrice());
            }

            //是否已经撮合完成 委托量已经撮合量和新的委托量相等
            if (tVol == cmd.volume) {
                break;
            }

        }

        //空列表删除
        emptyBuckets.forEach(matchingBuckets::remove);

        return tVol;

    }

    /**
     * 生成matchEvent
     *
     * @param cmd
     * @param status
     */
    private void genMatchEvent(RbCmd cmd, OrderStatus status) {
        long now = System.currentTimeMillis();
        MatchEvent event = new MatchEvent();
        event.timestamp = now;
        event.mid = cmd.mid;
        event.oid = cmd.oid;
        event.status = status;
        event.volume = 0;
        //加入撮合事件集合
        cmd.matchEventList.add(event);
    }

    /**
     * 取消委托
     * @param cmd
     * @return
     */
    @Override
    public CmdResultCode cancelOrder(RbCmd cmd) {
        //1.从缓存中移除委托
        Order order = oidMap.get(cmd.oid);
        if (order == null) {
            return CmdResultCode.INVALID_ORDER_ID;
        }
        //删除订单
        oidMap.remove(order.getOid());

        //2.从对应orderbucket中移除委托
        final NavigableMap<Long, IOrderBucket> buckets = order.getDirection() == OrderDirection.SELL ? sellBuckets : buyBuckets;
        //根据价格 获取Bucket
        IOrderBucket orderBucket = buckets.get(order.getPrice());
        //移除Bucket中 委托订单信息
        orderBucket.remove(order.getOid());
        //是否还有量
        if (orderBucket.getTotalVolume() == 0) {
            //没有就去掉当前bucket
            buckets.remove(order.getPrice());
        }

        //3.发送撤单MatchEvent
        MatchEvent cancelEvent = new MatchEvent();
        cancelEvent.timestamp = System.currentTimeMillis();
        cancelEvent.mid = order.getMid();
        cancelEvent.oid = order.getOid();
        //判断是部分撤单还是完全
        cancelEvent.status = order.getTvolume() == 0 ? OrderStatus.CANCEL_ED : OrderStatus.PART_CANCEL;
        cancelEvent.volume = order.getTvolume() - order.getVolume();
        cmd.matchEventList.add(cancelEvent);


        return CmdResultCode.SUCCESS;
    }

    @Override
    public void fillCode(L1MarketData data) {
        data.code = code;
    }

    @Override
    public void fillSells(int size, L1MarketData data) {
        // 0不用填
        if (size == 0) {
            data.sellSize = 0;
            return;
        }

        int i = 0;
        //遍历对应Bucket集合
        for (IOrderBucket bucket : sellBuckets.values()) {
            //设置几档价格数组
            data.sellPrices[i] = bucket.getPrice();
            //设置几档量数组
            data.sellVolumes[i] = bucket.getTotalVolume();
            if (++i == size) {
                break;
            }
        }

        //买卖实际档位数量
        data.sellSize = i;

    }

    @Override
    public void fillBuys(int size, L1MarketData data) {
        // 0不用填
        if (size == 0) {
            data.buySize = 0;
            return;
        }

        int i = 0;
        //遍历对应Bucket集合
        for (IOrderBucket bucket : buyBuckets.values()) {
            //设置几档价格数组
            data.buyPrices[i] = bucket.getPrice();
            //设置几档量数组
            data.buyVolumes[i] = bucket.getTotalVolume();
            if (++i == size) {
                break;
            }
        }

        //买卖实际档位数量
        data.buySize = i;
    }

    @Override
    public int limitBuyBucketSize(int maxSize) {
        return Math.min(maxSize, buyBuckets.size());
    }

    @Override
    public int limitSellBucketSize(int maxSize) {
        return Math.min(maxSize, sellBuckets.size());
    }
}
