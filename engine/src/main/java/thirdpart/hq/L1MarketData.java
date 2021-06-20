package thirdpart.hq;

import lombok.ToString;

import java.io.Serializable;

/**
 * 一档行情
 */
@ToString
public class L1MarketData implements Serializable {
    /**
     * 5档
     */
    @ToString.Exclude
    public static final int L1_SIZE = 5;

    /**
     * 股票代码
     */
    public int code;

    /**
     * 最新价格
     */
    public long newPrice;

    /**
     * 买卖实际档位数量
     */
    @ToString.Exclude
    public transient int buySize;

    @ToString.Exclude
    public transient int sellSize;

    /**
     * 买一  买二 卖一 等
     * 每个档位的价格和量
     */
    public long[] buyPrices;
    public long[] buyVolumes;
    public long[] sellPrices;
    public long[] sellVolumes;

    /**
     * 时间戳
     */
    public long timestamp;

    public L1MarketData(long[] buyPrices, long[] buyVolumes,
                        long[] sellPrices, long[] sellVolumes) {
        this.buyPrices = buyPrices;
        this.buyVolumes = buyVolumes;
        this.sellPrices = sellPrices;
        this.sellVolumes = sellVolumes;

        this.buySize = buyPrices.length;
        this.sellSize = sellPrices.length;
    }

    public L1MarketData(int buySize, int sellSize) {
        this.buyPrices = new long[buySize];
        this.buyVolumes = new long[buySize];
        this.sellPrices = new long[sellSize];
        this.sellVolumes = new long[sellSize];
    }


}
