package com.gudy.seq.bean;

import com.alipay.sofa.jraft.rhea.options.PlacementDriverOptions;
import com.alipay.sofa.jraft.rhea.options.RheaKVStoreOptions;
import com.alipay.sofa.jraft.rhea.options.StoreEngineOptions;
import com.alipay.sofa.jraft.rhea.options.configured.MemoryDBOptionsConfigured;
import com.alipay.sofa.jraft.rhea.options.configured.PlacementDriverOptionsConfigured;
import com.alipay.sofa.jraft.rhea.options.configured.RheaKVStoreOptionsConfigured;
import com.alipay.sofa.jraft.rhea.options.configured.StoreEngineOptionsConfigured;
import com.alipay.sofa.jraft.rhea.storage.StorageType;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.rpc.config.ConsumerConfig;
import com.alipay.sofa.rpc.listener.ChannelListener;
import com.alipay.sofa.rpc.transport.AbstractChannel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import thirdpart.codec.IBodyCodec;
import thirdpart.fetchsurv.IFetchService;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;

@Log4j2
@ToString
@RequiredArgsConstructor
public class SeqConfig {

    /**
     * 日志落地
     */
    private String dataPath;


    /**
     * 服务地址
     */
    private String serveUrl;

    /**
     * 服务列表
     */
    private String serverList;


    @NonNull
    private String fileName;

    public void startup() throws Exception {
        //1.读取配置文件
        initConfig();

        //2.初始化kv store集群
        startSeqDbCluster();

        //3.启动下游广播
        startMultiCast();

        //4.初始化网关连接
        startupFetch();


    }


    /////////////////////////////广播/////////////////////////////////////////////

    @Getter
    private String multicastIp;

    @Getter
    private int multicastPort;


    /**
     * 往外发送UDP 包的类
     */
    @Getter
    private DatagramSocket multicastSender;

    /**
     * 开启组播
     */
    private void startMultiCast() {
        //使用 默认参数 DatagramSocketOptions
        multicastSender = Vertx.vertx().createDatagramSocket(new DatagramSocketOptions());
    }


    /////////////////////////////抓取逻辑/////////////////////////////////////////////

    private String fetchurls;

    @ToString.Exclude
    @Getter
    private Map<String, IFetchService> fetchServiceMap = Maps.newConcurrentMap();

    @NonNull
    @ToString.Exclude
    @Getter
    private IBodyCodec codec;

    @RequiredArgsConstructor
    private class FetchChannelListener implements ChannelListener {

        @NonNull
        private ConsumerConfig<IFetchService> config;


        /**
         * 第一次创建链接不会执行 onConnected
         * 断开重连才会进入
         * @param channel
         */
        @Override
        public void onConnected(AbstractChannel channel) {
            String remoteAddr = channel.remoteAddress().toString();
            log.info("connect to gatewat : {}", remoteAddr);
            //保存当前链接
            fetchServiceMap.put(remoteAddr, config.refer());
        }

        @Override
        public void onDisconnected(AbstractChannel channel) {
            String remoteAddr = channel.remoteAddress().toString();
            log.info("disconnect from gatewat : {}", remoteAddr);
            //去掉当前链接
            fetchServiceMap.remove(remoteAddr);
        }
    }

    //1.从哪些网关抓取
    //2.通信方式
    private void startupFetch() {
        //1.建立所有到网关的连接
        String[] urls = fetchurls.split(";");
        for (String url : urls) {
            //抓取处理配置
            ConsumerConfig<IFetchService> consumerConfig = new ConsumerConfig<IFetchService>()
                    //通信接口 通讯标准
                    .setInterfaceId(IFetchService.class.getName())
                    //RPC通信协议
                    .setProtocol("bolt")
                    //超时时间
                    .setTimeout(5000)
                    //直连地址
                    .setDirectUrl(url);

            //设置消费者链接处理器
            consumerConfig.setOnConnect(Lists.newArrayList(new FetchChannelListener(consumerConfig)));
            //当前循环的链接放入map
            fetchServiceMap.put(url, consumerConfig.refer());
        }

        //2.定时抓取数据的任务
        new Timer().schedule(new FetchTask(this), 5000, 1000);


    }


    //////////////////////////////////////////////////////////////////////////

    @Getter
    private Node node;

    //启动KV Store
    private void startSeqDbCluster() {

        //管理多个集群
        final PlacementDriverOptions pdOpts = PlacementDriverOptionsConfigured.newConfigured()
                //没有必要调用自己
                .withFake(true)
                .config();


        // 127.0.0.1:8891
        String[] split = serveUrl.split(":");
        //存储引擎
        final StoreEngineOptions storeOpts = StoreEngineOptionsConfigured.newConfigured()
                //存储形式 内存
                .withStorageType(StorageType.Memory)
                //内存DB 默认设置
                .withMemoryDBOptions(MemoryDBOptionsConfigured.newConfigured().config())
                //存储位置
                .withRaftDataPath(dataPath)
                //服务地址
                .withServerAddress(new Endpoint(split[0], Integer.parseInt(split[1])))
                .config();

        //kv 数据库配置 配置前面的配置
        final RheaKVStoreOptions opts = RheaKVStoreOptionsConfigured.newConfigured()
                .withInitialServerList(serverList)
                .withStoreEngineOptions(storeOpts)
                .withPlacementDriverOptions(pdOpts)
                .config();

        //开启节点
        node = new Node(opts);
        node.start();
        //监听停止 执行节点关闭
        Runtime.getRuntime().addShutdownHook(new Thread(node::stop));
        log.info("start seq node success on port : {}", split[1]);

    }

    /**
     * 读取配置文件
     * @throws IOException
     */
    private void initConfig() throws IOException {
        Properties properties = new Properties();
        properties.load(Object.class.getResourceAsStream("/" + fileName));
        //本地数据存储地址
        dataPath = properties.getProperty("datapath");
        //服务地址
        serveUrl = properties.getProperty("serveurl");
        //接地地址数组
        serverList = properties.getProperty("serverlist");
        //取数据地址（网关）
        fetchurls = properties.getProperty("fetchurls");
        multicastIp = properties.getProperty("multicastip");
        multicastPort = Integer.parseInt(properties.getProperty("multicastport"));

        log.info("read config : {}", this);


    }


}
