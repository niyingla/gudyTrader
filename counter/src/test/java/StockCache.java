import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.builder.ToStringSummary;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

/**
 * 缓存股票信息
 */
public class StockCache {

//    private Map<String,List<Stock>>

    private Multimap<String, Stock> invertIndex = HashMultimap.create();

    public Collection<Stock> getStocks(String key){
        return invertIndex.get(key);
    }

    public void init() {
        //mongo oracle redis 拉取信息
        Stock s1 = new Stock(600519, "gzmt", "贵州茅台");
        Stock s2 = new Stock(2, "wka", "万科A");
        Stock s3 = new Stock(300059, "dfcf", "东方财富");
        List<Stock> stockList = Lists.newArrayList();
        stockList.add(s1);
        stockList.add(s2);
        stockList.add(s3);
        for (Stock s : stockList) {
            //2 --> 000002
            List<String> meta1 = splitData(String.format("%06d", s.getCode()));
            List<String> meta2 = splitData(s.getAbbrName());
            meta2.addAll(meta1);

            for(String key : meta2){
                Collection<Stock> stocks = invertIndex.get(key);
                if(!CollectionUtils.isEmpty(stocks) && stocks.size() > 10){
                    continue;
                }
                invertIndex.put(key,s);
            }
        }
    }

    private List<String> splitData(String code) {
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


    @Getter
    @AllArgsConstructor
    @ToString
    private class Stock {
        private int code;

        //平安银行 payh
        private String abbrName;

        private String name;

    }


    public static void main(String[] args) {
        StockCache cache = new StockCache();
        cache.init();

//        Collection<Stock> mt = cache.getStocks("mt");
//        System.out.println(mt);

        //代码
        Collection<Stock> mt = cache.getStocks("00");
        System.out.println(mt);


    }

}
