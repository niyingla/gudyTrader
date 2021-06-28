package com.gudy.engine.bean.orderbook;

import com.gudy.engine.bean.command.RbCmd;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public interface IOrderBucket extends Comparable<IOrderBucket> {


    /**
     * 订单号生成
     */
    AtomicLong tidGen = new AtomicLong(0);

    //1.新增订单
    void put(Order order);

    //2.移除订单
    Order remove(long oid);


    /**
     * match 撮合
     * @param volumeLeft 匹配数量
     * @param triggerCmd 委托单信息
     * @param removeOrderCallback 成功匹配回调函数
     * @return
     */
    long match(long volumeLeft, RbCmd triggerCmd, Consumer<Order> removeOrderCallback);

    //4.行情发布
    long getPrice();

    /**
     * 设置行情价格
     * @param price
     */
    void setPrice(long price);

    /**
     * d当前桶 累计委托量
     * @return
     */
    long getTotalVolume();


    /**
     * 初始化选项
     * @param type
     * @return
     */
    static IOrderBucket create(OrderBucketImplType type) {
        switch (type) {
            case GUDY:
                return new GOrderBucketImpl();
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * 定义 OrderBucket 类型枚举
     */
    @Getter
    enum OrderBucketImplType {
        GUDY(0);

        private byte code;

        OrderBucketImplType(int code) {
            this.code = (byte) code;
        }
    }


    //6.比较 排序
    @Override
    default int compareTo(IOrderBucket other) {
        return Long.compare(this.getPrice(), other.getPrice());
    }

}
