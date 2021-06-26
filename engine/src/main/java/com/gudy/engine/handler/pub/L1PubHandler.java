package com.gudy.engine.handler.pub;

import com.gudy.engine.bean.EngineConfig;
import com.gudy.engine.bean.command.RbCmd;
import com.gudy.engine.bean.orderbook.MatchEvent;
import com.gudy.engine.handler.BaseHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.eclipse.collections.api.tuple.primitive.ShortObjectPair;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ShortObjectHashMap;
import thirdpart.bean.CommonMsg;
import thirdpart.hq.L1MarketData;
import thirdpart.hq.MatchData;
import thirdpart.order.CmdType;

import java.util.List;

import static thirdpart.bean.MsgConstants.MATCH_HQ_DATA;
import static thirdpart.bean.MsgConstants.MATCH_ORDER_DATA;
import static thirdpart.bean.MsgConstants.NORMAL;

@Log4j2
@RequiredArgsConstructor
public class L1PubHandler extends BaseHandler {
    /***
     * 发布时间间隔
     */
    public static final int HQ_PUB_RATE = 1000;


    /**
     * key 柜台id value 发送数据（随着行情发送）
     */
    @NonNull
    private final ShortObjectHashMap<List<MatchData>> matcherEventMap;

    @NonNull
    private EngineConfig config;


    /**
     * 接受事件
     * @param cmd
     * @param sequence
     * @param endOfBatch
     * @throws Exception
     */
    @Override
    public void onEvent(RbCmd cmd, long sequence, boolean endOfBatch) throws Exception {
        final CmdType cmdType = cmd.command;

        //下单、撤单请求
        if (cmdType == CmdType.NEW_ORDER || cmdType == CmdType.CANCEL_ORDER) {
            //循环克隆撮合结果到matcherEventMap
            for (MatchEvent e : cmd.matchEventList) {
                //放入撮合成功集合
                matcherEventMap.get(e.mid).add(e.copy());
            }
        } else if (cmdType == CmdType.HQ_PUB) {
            //1.五档行情
            pubMarketData(cmd.marketDataMap);
            //2.给柜台发送MatchData
            pubMatcherData();
        }

    }

    /**
     * 给柜台发送MatchData 撮合信息
     */
    private void pubMatcherData() {
        if (matcherEventMap.size() == 0) {
            return;
        }

        log.info(matcherEventMap);

        try {
            for (ShortObjectPair<List<MatchData>> s : matcherEventMap.keyValuesView()) {
                if (CollectionUtils.isEmpty(s.getTwo())) {
                    continue;
                }
                //序列化数据
                byte[] serialize = config.getBodyCodec().serialize(s.getTwo().toArray(new MatchData[0]));

                //推送撮合数据
                pubData(serialize, s.getOne(), MATCH_ORDER_DATA);

                //清空已发送数据
                s.getTwo().clear();

            }
        } catch (Exception e) {
            log.error(e);
        }

    }

    /**
     * 约定这个（柜台）地址 就是五档行情
     */
    public static final short HQ_ADDRESS = -1;

    /**
     * 生成五档行情
     * @param marketDataMap
     */
    private void pubMarketData(IntObjectHashMap<L1MarketData> marketDataMap) {
        log.info(marketDataMap);
        byte[] serialize = null;
        try {
            //进行五档行情数据序列化
            serialize = config.getBodyCodec().serialize(marketDataMap.values().toArray(new L1MarketData[0]));
        } catch (Exception e) {
            log.error(e);
        }

        if (serialize == null) {
            return;
        }

        //发布行情数据
        pubData(serialize, HQ_ADDRESS, MATCH_HQ_DATA);

    }

    /**
     * 推送总线数据
     * @param serialize
     * @param dst
     * @param msgType
     */
    private void pubData(byte[] serialize, short dst, short msgType) {
        CommonMsg msg = new CommonMsg();
        msg.setBodyLength(serialize.length);
        msg.setChecksum(config.getCs().getChecksum(serialize));
        msg.setMsgSrc(config.getId());
        msg.setMsgDst(dst);
        msg.setMsgType(msgType);
        msg.setStatus(NORMAL);
        msg.setBody(serialize);
        config.getBusSender().publish(msg);
    }
}
