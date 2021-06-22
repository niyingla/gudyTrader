package com.gudy.engine.bean;

import com.alipay.sofa.jraft.rhea.client.DefaultRheaKVStore;
import com.alipay.sofa.jraft.rhea.client.RheaKVStore;
import com.alipay.sofa.jraft.rhea.options.PlacementDriverOptions;
import com.alipay.sofa.jraft.rhea.options.RegionRouteTableOptions;
import com.alipay.sofa.jraft.rhea.options.RheaKVStoreOptions;
import com.alipay.sofa.jraft.rhea.options.configured.MultiRegionRouteTableOptionsConfigured;
import com.alipay.sofa.jraft.rhea.options.configured.PlacementDriverOptionsConfigured;
import com.alipay.sofa.jraft.rhea.options.configured.RheaKVStoreOptionsConfigured;
import com.google.common.collect.Lists;
import com.gudy.engine.bean.orderbook.GOrderBookImpl;
import com.gudy.engine.bean.orderbook.IOrderBook;
import com.gudy.engine.core.EngineApi;
import com.gudy.engine.core.EngineCore;
import com.gudy.engine.db.DbQuery;
import com.gudy.engine.handler.BaseHandler;
import com.gudy.engine.handler.match.StockMatchHandler;
import com.gudy.engine.handler.pub.L1PubHandler;
import com.gudy.engine.handler.risk.ExistRiskHandler;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.dbutils.QueryRunner;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ShortObjectHashMap;
import thirdpart.bean.CmdPack;
import thirdpart.bus.IBusSender;
import thirdpart.bus.MqttBusSender;
import thirdpart.checksum.ICheckSum;
import thirdpart.codec.IBodyCodec;
import thirdpart.codec.IMsgCodec;
import thirdpart.hq.MatchData;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.*;

@Log4j2
@ToString
@Getter
@RequiredArgsConstructor
public class EngineConfig {

    private short id;

    private String orderRecvIp;
    private int orderRecvPort;

    private String seqUrlList;

    private String pubIp;
    private int pubPort;

    @NonNull
    private String fileName;

    @NonNull
    private IBodyCodec bodyCodec;

    @NonNull
    private ICheckSum cs;

    @NonNull
    private IMsgCodec msgCodec;

    private Vertx vertx = Vertx.vertx();


    public void startup() throws Exception {
        //1.读取配置文件
        initConfig();

        //2.数据库连接
        initDB();

        //3.启动撮合核心
        startEngine();

        //4.建立总线连接 初始化数据的发送
        initPub();

        //5.初始化接收排队机数据以及连接
        startSeqConn();


    }

    ////////////////////////////////////////////////////////////////////


    @Getter
    private IBusSender busSender;

    private void initPub() {
        //创建总线消息发送类
        busSender = new MqttBusSender(pubIp, pubPort, msgCodec, vertx);
        //启动链接
        busSender.startup();
    }

    /////////////////////////开启撮合引擎///////////////////////////////////////////

    private void startEngine() throws Exception {

        //1.前置风控处理器
        final BaseHandler riskHandler = new ExistRiskHandler(
                //查询所有资金
                db.queryAllBalance().keySet(),
                //查询所有持仓
                db.queryAllStockCode()
        );


        //2.撮合处理器(订单簿*****) 撮合/提供行情查询
        IntObjectHashMap<IOrderBook> orderBookMap = new IntObjectHashMap<>();
        //每个股票代码都定义订单簿
        db.queryAllStockCode().forEach(code -> orderBookMap.put(code, new GOrderBookImpl(code)));
        //存到撮合handler
        final BaseHandler matchHandler = new StockMatchHandler(orderBookMap);

        //3.发布处理器 key 柜台id value 发布数据
        ShortObjectHashMap<List<MatchData>> matcherEventMap = new ShortObjectHashMap<>();
        //查询所有会员id （对应的就是 柜台id）
        for (short id : db.queryAllMemberIds()) {
            //放入撮合
            matcherEventMap.put(id, Lists.newArrayList());
        }

        //创建发布行情处理器
        final BaseHandler pubHandler = new L1PubHandler(matcherEventMap, this);


        //设置到撮合核心
        engineApi = new EngineCore(
                riskHandler,
                matchHandler,
                pubHandler
        ).getApi();

    }

    ////////////////////////////////数据库查询///////////////////////////////////////////
    @Getter
    private DbQuery db;

    /**
     * 初始化数据库
     */
    private void initDB() {
        QueryRunner runner = new QueryRunner(new ComboPooledDataSource());
        db = new DbQuery(runner);
    }

    @Getter
    private EngineApi engineApi;

    @Getter
    @ToString.Exclude
    private final RheaKVStore orderKvStore = new DefaultRheaKVStore();

    /**
     * 连接排队机
     */
    private void startSeqConn() throws Exception {
        final List<RegionRouteTableOptions> regionRouteTableOptions
                = MultiRegionRouteTableOptionsConfigured
                .newConfigured()
                .withInitialServerList(-1L, seqUrlList)
                .config();

        final PlacementDriverOptions pdOpts = PlacementDriverOptionsConfigured
                .newConfigured()
                .withFake(true)
                .withRegionRouteTableOptionsList(regionRouteTableOptions)
                .config();

        final RheaKVStoreOptions opts = RheaKVStoreOptionsConfigured
                .newConfigured()
                .withPlacementDriverOptions(pdOpts)
                .config();

        //初始化一致性数据缓存
        orderKvStore.init(opts);

        /////////////////////////////////////////////////////////////////////

        //委托指令处理器
        CmdPacketQueue.getInstance().init(orderKvStore, bodyCodec, engineApi);

        //组播接受 允许多个Socket接收同一份数据
        DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
        //监听数据包
        socket.listen(orderRecvPort, "0.0.0.0", asyncRes -> {
            if (asyncRes.succeeded()) {

                socket.handler(packet -> {
                    //拿到组播数据
                    Buffer udpData = packet.data();
                    if (udpData.length() > 0) {
                        try {
                            //反序列化
                            CmdPack cmdPack = bodyCodec.deserialize(udpData.getBytes(), CmdPack.class);
                            //加入下单数据缓存
                            CmdPacketQueue.getInstance().cache(cmdPack);
                        } catch (Exception e) {
                            log.error("decode packet error", e);
                        }
                    } else {
                        log.error("recv empty udp packet from client : {}", packet.sender().toString());
                    }
                });


                try {
                    //监听组播 允许多个Socket接收同一份数据
                    socket.listenMulticastGroup(
                            //组播id
                            orderRecvIp,
                            //网卡名字
                            mainInterface().getName(),
                            //发消息 原地址 （这里不用知道）
                            null,
                            //异步处理器 判断是否成功
                            asyncRes2 -> {
                                log.info("listen succeed {}", asyncRes2.succeeded());
                            });
                } catch (Exception e) {
                    log.error(e);
                }
            } else {
                //监听失败 日志
                log.error(" Listen failed ,", asyncRes.cause());
            }
        });


    }

    /**
     * 获取网卡
     * @return
     * @throws Exception
     */
    private static NetworkInterface mainInterface() throws Exception {
        //获取本地网卡
        final ArrayList<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        //过滤不可用
        final NetworkInterface networkInterface = interfaces.stream().filter(t -> {
            //1. !loopback
            //2.支持multicast
            //3. 非虚拟机网卡
            //4. 有IPV4
            try {
                final boolean isLoopback = t.isLoopback();
                final boolean supportMulticast = t.supportsMulticast();
                final boolean isVirtualBox = t.getDisplayName().contains("VirtualBox") || t.getDisplayName().contains("Host-only");
                final boolean hasIpv4 = t.getInterfaceAddresses().stream().anyMatch(ia -> ia.getAddress() instanceof Inet4Address);
                return !isLoopback && supportMulticast && !isVirtualBox && hasIpv4;
            } catch (Exception e) {
                log.error("fine net interface error", e);
            }
            return false;
        }).sorted(Comparator.comparing(NetworkInterface::getName)).findFirst().orElse(null);

        return networkInterface;
    }

    /**
     * 初始化配置
     *
     * @throws Exception
     */
    private void initConfig() throws Exception {
        Properties properties = new Properties();
        properties.load(this.getClass().getResourceAsStream("/" + fileName));

        id = Short.parseShort(properties.getProperty("id"));

        orderRecvIp = properties.getProperty("orderrecvip");
        orderRecvPort = Integer.parseInt(properties.getProperty("orderrecvport"));

        seqUrlList = properties.getProperty("sequrllist");

        pubIp = properties.getProperty("pubip");
        pubPort = Integer.parseInt(properties.getProperty("pubport"));

        log.info(this);

    }


}
