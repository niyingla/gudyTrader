package com.gudy.counter.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.gudy.counter.bean.res.*;
import com.gudy.counter.cache.CacheType;
import com.gudy.counter.cache.RedisStringCache;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import thirdpart.hq.MatchData;
import thirdpart.order.OrderCmd;
import thirdpart.order.OrderStatus;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;


/**
 * 单例数据库工具类
 */
@Log4j2
//第二步
@Component
public class DbUtil {

    private static DbUtil dbUtil = null;

    //    第一步
    private DbUtil() {
    }

    //第三部
    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

    //第四部
    public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    public SqlSessionTemplate getSqlSessionTemplate() {
        return sqlSessionTemplate;
    }

    //第二步
    @PostConstruct
    private void init() {
        dbUtil = new DbUtil();
        //第四部
        dbUtil.setSqlSessionTemplate(this.sqlSessionTemplate);
    }

    //////////////////////////////身份认证/////////////////////////////////////
    public static Account queryAccount(long uid, String password) {
        //指定sql 和参数 执行
        return dbUtil.getSqlSessionTemplate().selectOne("userMapper.queryAccount", ImmutableMap.of("UId", uid, "Password", password));
    }

    public static void updateLoginTime(long uid, String nowDate, String nowTime) {
        dbUtil.getSqlSessionTemplate().update("userMapper.updateAccountLoginTime", ImmutableMap.of("UId", uid, "ModifyDate", nowDate, "ModifyTime", nowTime)
        );
    }

    public static int updatePwd(long uid, String oldPwd, String newPwd) {
        return dbUtil.getSqlSessionTemplate().update("userMapper.updatePwd", ImmutableMap.of("UId", uid, "NewPwd", newPwd, "OldPwd", oldPwd));
    }

    //////////////////////////////资金类////////////////////////////////////////
    public static long getBalance(long uid) {
        Long res = dbUtil.getSqlSessionTemplate().selectOne("orderMapper.queryBalance", ImmutableMap.of("UId", uid));
        if (res == null) {
            return -1;
        } else {
            return res;
        }
    }

    public static void addBalance(long uid, long balance) {
        dbUtil.getSqlSessionTemplate().update("orderMapper.updateBalance", ImmutableMap.of("UId", uid, "Balance", balance));
    }

    public static void minusBalance(long uid, long balance) {
        addBalance(uid, -balance);
    }

    //////////////////////////////持仓类////////////////////////////////////////
    public static List<PosiInfo> getPosiList(long uid) {
        //查缓存
        String suid = Long.toString(uid);
        String posiS = RedisStringCache.get(suid, CacheType.POSI);
        if (StringUtils.isEmpty(posiS)) {
            //未查到 查库
            List<PosiInfo> tmp = dbUtil.getSqlSessionTemplate().selectList("orderMapper.queryPosi", ImmutableMap.of("UId", uid));
            List<PosiInfo> result = CollectionUtils.isEmpty(tmp) ? Lists.newArrayList() : tmp;
            //更新缓存
            RedisStringCache.cache(suid, JsonUtil.toJson(result), CacheType.POSI);
            return result;
        } else {
            //查到 命中缓存
            return JsonUtil.fromJsonArr(posiS, PosiInfo.class);
        }
    }

    public static PosiInfo getPosi(long uid, int code) {
        return dbUtil.getSqlSessionTemplate().selectOne("orderMapper.queryPosi", ImmutableMap.of("UId", uid, "Code", code));
    }

    public static void addPosi(long uid, int code, long volume, long price) {
        //持仓是否存在
        PosiInfo posiInfo = getPosi(uid, code);
        if (posiInfo == null) {
            //新增一条持仓
            insertPosi(uid, code, volume, price);
        } else {
            //修改持仓
            posiInfo.setCount(posiInfo.getCount() + volume);
            posiInfo.setCost(posiInfo.getCost() + price * volume);
//            if(posiInfo.getCount() == 0){
//                deletePosi(posi);
//            }else {
            updatePosi(posiInfo);
//            }

        }
    }

    public static void minusPosi(long uid, int code, long volume, long price) {
        addPosi(uid, code, -volume, price);
    }

    private static void updatePosi(PosiInfo posiInfo) {
        dbUtil.getSqlSessionTemplate().insert("orderMapper.updatePosi",
                ImmutableMap.of("UId", posiInfo.getUid(),
                        "Code", posiInfo.getCode(),
                        "Count", posiInfo.getCount(),
                        "Cost", posiInfo.getCost())
        );
    }

    private static void insertPosi(long uid, int code, long volume, long price) {
        dbUtil.getSqlSessionTemplate().insert("orderMapper.insertPosi",
                ImmutableMap.of("UId", uid, "Code", code,
                        "Count", volume,
                        "Cost", volume * price)
        );
    }

    //////////////////////////////委托类////////////////////////////////////////
    public static List<OrderInfo> getOrderList(long uid) {
        //查缓存
        String suid = Long.toString(uid);
        String orderS = RedisStringCache.get(suid, CacheType.ORDER);
        if (StringUtils.isEmpty(orderS)) {
            //未查到 查库
            List<OrderInfo> tmp = dbUtil.getSqlSessionTemplate().selectList("orderMapper.queryOrder", ImmutableMap.of("UId", uid));
            List<OrderInfo> result = CollectionUtils.isEmpty(tmp) ? Lists.newArrayList() : tmp;
            //更新缓存
            RedisStringCache.cache(suid, JsonUtil.toJson(result), CacheType.ORDER);
            return result;
        } else {
            //查到 命中缓存
            return JsonUtil.fromJsonArr(orderS, OrderInfo.class);
        }
    }


    public static void updateOrder(long uid, int oid, OrderStatus status) {
        Map<String, Object> param = Maps.newHashMap();
        param.put("Id", oid);
        param.put("Status", status.getCode());
        dbUtil.getSqlSessionTemplate().update("orderMapper.updateOrder", param);

        RedisStringCache.remove(Long.toString(uid), CacheType.ORDER);
    }

    //////////////////////////////成交类////////////////////////////////////////
    public static List<TradeInfo> getTradeList(long uid) {
        //查缓存
        String suid = Long.toString(uid);
        String tradeS = RedisStringCache.get(suid, CacheType.TRADE);
        if (StringUtils.isEmpty(tradeS)) {
            //未查到 查库
            List<TradeInfo> tmp = dbUtil.getSqlSessionTemplate().selectList(
                    "orderMapper.queryTrade",
                    ImmutableMap.of("UId", uid)
            );
            List<TradeInfo> result =
                    CollectionUtils.isEmpty(tmp) ? Lists.newArrayList()
                            : tmp;
            //更新缓存
            RedisStringCache.cache(suid, JsonUtil.toJson(result), CacheType.TRADE);
            return result;
        } else {
            //查到 命中缓存
            return JsonUtil.fromJsonArr(tradeS, TradeInfo.class);
        }
    }

    public static void saveTrade(int counterOId, MatchData md, OrderCmd orderCmd) {
        if (orderCmd == null) {
            return;
        }
        Map<String, Object> param = Maps.newHashMap();
        param.put("Id", md.tid);
        param.put("UId", orderCmd.uid);
        param.put("Code", orderCmd.code);
        param.put("Direction", orderCmd.direction.getDirection());
        param.put("Price", md.price);
        param.put("TCount", md.volume);
        param.put("OId", counterOId);//高位为counterid)
        param.put("Date", TimeformatUtil.yyyyMMdd(md.timestamp));
        param.put("Time", TimeformatUtil.hhMMss(md.timestamp));
        dbUtil.getSqlSessionTemplate().insert("orderMapper.saveTrade", param);

        //更新缓存
        RedisStringCache.remove(Long.toString(orderCmd.uid), CacheType.TRADE);

    }

    //////////////////////////////订单处理类///////////////////////////////////////
    public static int saveOrder(OrderCmd orderCmd) {
        Map<String, Object> param = Maps.newHashMap();
        param.put("UId", orderCmd.uid);
        param.put("Code", orderCmd.code);
        param.put("Direction", orderCmd.direction.getDirection());
        param.put("Type", orderCmd.orderType.getType());
        param.put("Price", orderCmd.price);
        param.put("OCount", orderCmd.volume);
        param.put("TCount", 0);
        param.put("Status", OrderStatus.NOT_SET.getCode());

        param.put("Data", TimeformatUtil.yyyyMMdd(orderCmd.timestamp));
        param.put("Time", TimeformatUtil.hhMMss(orderCmd.timestamp));

        int count = dbUtil.getSqlSessionTemplate().insert(
                "orderMapper.saveOrder", param
        );
        //判断是否成功
        if (count > 0) {
            return Integer.parseInt(param.get("ID").toString());
        } else {
            return -1;
        }
    }


    //////////////////////////////股票信息查询///////////////////////////////////////
    public static List<Map<String, Object>> queryAllSotckInfo() {
        return dbUtil.getSqlSessionTemplate()
                .selectList("stockMapper.queryStock");
    }


}
