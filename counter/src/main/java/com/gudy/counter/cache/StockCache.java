package com.gudy.counter.cache;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.gudy.counter.bean.res.StockInfo;
import com.gudy.counter.util.DbUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
@DependsOn("counterConfig")
public class StockCache {

    //Map<String,List<StockInfo>>
    // 6 --> 600086,600025...
    private HashMultimap<String, StockInfo> invertIndex = HashMultimap.create();

    /**
     * 多结果集返回（多次放入一个key 取的时候返回多次的结果）
     * @param key
     * @return
     */
    public Collection<StockInfo> getStocks(String key) {
        return invertIndex.get(key);
    }

    /**
     * 倒排索引 所有关键字 作为索引
     */
    @PostConstruct
    private void createInvertIndex() {
        log.info("load stock from db");
        long st = System.currentTimeMillis();

        //1.加载股票数据
        List<Map<String, Object>> res = DbUtil.queryAllSotckInfo();
        if (CollectionUtils.isEmpty(res)) {
            log.error("no stock find in db");
            return;
        }
        //2.建立倒排索引
        for (Map<String, Object> r : res) {
            int code = Integer.parseInt(r.get("code").toString());
            String name = r.get("name").toString();
            String abbrname = r.get("abbrname").toString();
            StockInfo stock = new StockInfo(code, name, abbrname);
            //  000001 平安银行
            List<String> codeMetas = splitData(String.format("%06d", code));
            // payh 平安银行
            List<String> abbrNameMetas = splitData(abbrname);
            codeMetas.addAll(abbrNameMetas);

            for (String key : codeMetas) {
                //限制索引数据列表长度
                Collection<StockInfo> stockInfos = invertIndex.get(key);
                //大于是个不继续加入
                if (!CollectionUtils.isEmpty(stockInfos) && stockInfos.size() > 10) {
                    continue;
                }
                invertIndex.put(key, stock);
            }
        }
        log.info("load stock finish,take :" + (System.currentTimeMillis() - st) + "ms");
    }

    private List<String> splitData(String code) {
        // payh -->
        // p pa pay payh
        // a ay ayh
        // y yh
        // h
        List<String> list = Lists.newArrayList();
        int outLength = code.length();
        for (int i = 0; i < outLength; i++) {
            int inLength = outLength + 1;
            for (int j = i + 1; j < inLength; j++) {
                list.add(code.substring(i, j));
            }
        }
        return list;
    }

}
