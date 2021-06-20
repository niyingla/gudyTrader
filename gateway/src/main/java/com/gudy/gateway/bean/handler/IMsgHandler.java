package com.gudy.gateway.bean.handler;

import io.vertx.core.net.NetSocket;
import thirdpart.bean.CommonMsg;

/**
 * @author 10479
 */
public interface IMsgHandler {

    /**
     * 接收连接时
     * @param socket
     */
    default void onConnect(NetSocket socket){}

    /**
     * 断开
     * @param socket
     */
    default void onDisConnect(NetSocket socket){}


    /**
     * 异常
     * @param socket
     * @param e
     */
    default void onException(NetSocket socket,Throwable e){}

    /**
     * 柜台过来的消息
     * @param msg
     */
    void onCounterData(CommonMsg msg);
}
