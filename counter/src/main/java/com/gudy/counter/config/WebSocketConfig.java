package com.gudy.counter.config;

import io.vertx.core.Vertx;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Log4j2
@Configuration
public class WebSocketConfig {

    //五档行情
    public static final String L1_MARKET_DATA_PREFIX = "l1-market-data";

    //成交
    public final static String TRADE_NOTIFY_ADDR_PREFIX = "tradechange-";

    //委托
    public final static String ORDER_NOTIFY_ADDR_PREFIX = "orderchange-";


    /**
     * 1 委托和成交数据 柜台会通知终端 收到通知后 终端自己去取
     * 2 行情数据 定时拉取
     */
    @Autowired
    private CounterConfig config;

    @PostConstruct
    private void init() {
        Vertx vertx = config.getVertx();

        //只允许成交 委托的变动通过websocket总线往外发送
        BridgeOptions options = new BridgeOptions()
                //放行进来请求 行情请求
                .addInboundPermitted(new PermittedOptions().setAddress(L1_MARKET_DATA_PREFIX))
                //放行出去请求 委托通知
                .addOutboundPermitted(new PermittedOptions().setAddressRegex(ORDER_NOTIFY_ADDR_PREFIX + "[0-9]+"))
                //放行出去请求 成交通知
                .addOutboundPermitted(new PermittedOptions().setAddressRegex(TRADE_NOTIFY_ADDR_PREFIX + "[0-9]+"));

        //创建webSocket处理器
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

        //消息处理 1 连接设置 2 时间处理 （这里只是简单打印）
        sockJSHandler.bridge(options, event -> {
            //判断事件类型
            if (event.type() == BridgeEventType.SOCKET_CREATED) {
                log.info("client : {} connected", event.socket().remoteAddress());
            } else if (event.type() == BridgeEventType.SOCKET_CLOSED) {
                log.info("client : {} closed", event.socket().remoteAddress());
            }
            event.complete(true);
        });

        //接受总线消息
        Router router = Router.router(vertx);
        //指定socket url
        router.route("/eventbus/*").handler(sockJSHandler);
        //设置监听端口
        vertx.createHttpServer().requestHandler(router).listen(config.getPubPort());

    }
}
