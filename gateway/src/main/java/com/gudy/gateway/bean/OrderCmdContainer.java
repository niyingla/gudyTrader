package com.gudy.gateway.bean;

import com.google.common.collect.Lists;
import thirdpart.order.OrderCmd;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class OrderCmdContainer {

    private static OrderCmdContainer ourInstance = new OrderCmdContainer();

    private OrderCmdContainer(){}

    public static OrderCmdContainer getInstance(){
        return  ourInstance;
    }

    ////////////////////////////////

    /**
     * 阻塞队列
     */
    private final BlockingQueue<OrderCmd> queue = new LinkedBlockingDeque<>();

    /**
     * 提交订单
     * @param cmd
     * @return
     */
    public boolean cache(OrderCmd cmd){
        return queue.offer(cmd);
    }

    public int size(){
        return queue.size();
    }

    /**
     * 去除全部数据 清空缓存集合
     * @return
     */
    public List<OrderCmd> getAll(){
        List<OrderCmd> msgList = Lists.newArrayList();
        //不阻塞 一次取出所有 并清空数据
        int count  = queue.drainTo(msgList);
        if(count == 0){
            return null;
        }else {
            return msgList;
        }
    }

}
