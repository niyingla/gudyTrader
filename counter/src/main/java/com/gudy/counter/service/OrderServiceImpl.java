package com.gudy.counter.service;

import com.alipay.remoting.exception.CodecException;
import com.gudy.counter.bean.res.OrderInfo;
import com.gudy.counter.bean.res.PosiInfo;
import com.gudy.counter.bean.res.TradeInfo;
import com.gudy.counter.config.CounterConfig;
import com.gudy.counter.config.GatewayConn;
import com.gudy.counter.util.DbUtil;
import com.gudy.counter.util.IDConverter;
import io.vertx.core.buffer.Buffer;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import thirdpart.order.CmdType;
import thirdpart.order.OrderCmd;
import thirdpart.order.OrderDirection;
import thirdpart.order.OrderType;

import java.util.List;

import static com.gudy.counter.bean.MatchDataConsumer.ORDER_DATA_CACHE_ADDR;

@Log4j2
@Component
public class OrderServiceImpl implements IOrderService {
    @Override
    public Long getBalance(long uid) {
        return DbUtil.getBalance(uid);
    }

    @Override
    public List<PosiInfo> getPostList(long uid) {
        return DbUtil.getPosiList(uid);
    }

    @Override
    public List<OrderInfo> getOrderList(long uid) {
        return DbUtil.getOrderList(uid);
    }

    @Override
    public List<TradeInfo> getTradeList(long uid) {
        return DbUtil.getTradeList(uid);
    }

    @Autowired
    private CounterConfig config;

    @Autowired
    private GatewayConn gatewayConn;

    @Override
    public boolean sendOrder(long uid, short type, long timestamp, int code,
                             byte direction, long price, long volume, byte ordertype) {
        final OrderCmd orderCmd = OrderCmd.builder()
                .type(CmdType.of(type))
                .timestamp(timestamp)
                .mid(config.getId())
                .uid(uid)
                .code(code)
                .direction(OrderDirection.of(direction))
                .price(price)
                .volume(volume)
                .orderType(OrderType.of(ordertype))
                .build();

        //1.入库
        int oid = DbUtil.saveOrder(orderCmd);
        if (oid < 0) {
            return false;
        } else {
            //1.调整资金持仓数据
            if (orderCmd.direction == OrderDirection.BUY) {
                //减少资金
                DbUtil.minusBalance(orderCmd.uid, orderCmd.price * orderCmd.volume);
            } else if (orderCmd.direction == OrderDirection.SELL) {
                //减少持仓
                DbUtil.minusPosi(orderCmd.uid, orderCmd.code, orderCmd.volume, orderCmd.price);
            } else {
                log.error("wrong direction[{}],ordercmd:{}", orderCmd.direction, orderCmd);
                return false;
            }

            //2.生成全局ID  组装ID long [  柜台ID,  委托ID ]
            orderCmd.oid = IDConverter.combineInt2Long(config.getId(), oid);

            //保存委托到缓存
            byte[] serialize = null;
            try {
                //序列化委托订单
                serialize = config.getBodyCodec().serialize(orderCmd);
            } catch (Exception e) {
                log.error(e);
            }
            if (serialize == null) {
                return false;
            }

            //发送
            config.getVertx().eventBus().send(ORDER_DATA_CACHE_ADDR, Buffer.buffer(serialize));


            //3.打包委托(ordercmd --> commonmsg -->tcp数据流)
            // 4.发送数据
            gatewayConn.sendOrder(orderCmd);


            log.info(orderCmd);
            return true;
        }
    }


    @Override
    public boolean cancelOrder(int uid, int counteroid, int code) {

        final OrderCmd orderCmd = OrderCmd.builder()
                .uid(uid)
                .code(code)
                .type(CmdType.CANCEL_ORDER)
                .oid(IDConverter.combineInt2Long(config.getId(), counteroid))
                .build();

        log.info("recv cancel order :{}", orderCmd);

        gatewayConn.sendOrder(orderCmd);
        return true;
    }
}
