package com.gudy.counter.bean;

import com.gudy.counter.config.CounterConfig;
import com.gudy.counter.util.JsonUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import thirdpart.hq.L1MarketData;

import javax.annotation.PostConstruct;

import static com.gudy.counter.bean.MqttBusConsumer.INNER_MARKET_DATA_CACHE_ADDR;
import static com.gudy.counter.config.WebSocketConfig.L1_MARKET_DATA_PREFIX;

@Log4j2
@Component
public class MarketDataConsumer {

    @Autowired
    private CounterConfig config;

    //<key code,value 最新的五档行情 >
    private IntObjectHashMap<L1MarketData> l1Cache = new IntObjectHashMap<>();

    @PostConstruct
    private void init() {
        //获取总线
        EventBus eventBus = config.getVertx().eventBus();

        //撮合处理核心发过来的行情 进行消费(用于更新最新五档行情)
        eventBus.consumer(INNER_MARKET_DATA_CACHE_ADDR)
                .handler(buffer -> {
                    Buffer body = (Buffer) buffer.body();
                    if (body.length() == 0) {
                        return;
                    }
                    L1MarketData[] marketData = null;
                    try {
                        //解码
                        marketData = config.getBodyCodec().deserialize(body.getBytes(), L1MarketData[].class);
                    } catch (Exception e) {
                        log.error(e);
                    }

                    if (ArrayUtils.isEmpty(marketData)) {
                        return;
                    }

                    //循环行情信息
                    for (L1MarketData md : marketData) {
                        //获取缓存中的行情
                        L1MarketData l1MarketData = l1Cache.get(md.code);
                        //判断新老行情时间戳
                        if (l1MarketData == null || l1MarketData.timestamp < md.timestamp) {
                            //新的 放入行情缓存
                            l1Cache.put(md.code, md);
                        } else {
                            log.error("l1MarketData is null or l1MarketData.timestamp < md.timestamp");
                        }
                    }
                });


        //委托终端行情处理器 接受五档行情请求 数据来自 WebSocketConfig 接受的 L1_MARKET_DATA_PREFIX
        //用于回复五档行情求情
        eventBus.consumer(L1_MARKET_DATA_PREFIX)
                .handler(h -> {
                    int code = Integer.parseInt(h.headers().get("code"));
                    L1MarketData data = l1Cache.get(code);
                    //回复行情信息
                    h.reply(JsonUtil.toJson(data));
                });
    }

}
