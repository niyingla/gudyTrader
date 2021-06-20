package com.gudy.gateway.bean;

import com.alipay.sofa.rpc.config.ProviderConfig;
import com.alipay.sofa.rpc.config.ServerConfig;
import com.gudy.gateway.bean.handler.ConnHandler;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import thirdpart.checksum.ICheckSum;
import thirdpart.codec.IBodyCodec;
import thirdpart.fetchsurv.IFetchService;

import java.io.File;
import java.io.InputStream;

@Log4j2
@Getter
public class GatewayConfig {

    //网关ID
    private short id;

    //端口
    private int recvPort;

    //排队机通信Provider端口
    private int fetchServPort;


    //TODO 柜台列表 数据库连接

    @Setter
    private IBodyCodec bodyCodec;

    @Setter
    private ICheckSum cs;

    //创建实例
    private Vertx vertx = Vertx.vertx();


    /**
     * 初始化配置
     * @param in
     * @throws Exception
     */
    public void initConfig(InputStream in) throws Exception {
        // 创建dom4j 解析器
        SAXReader reader = new SAXReader();
        Document document = reader.read(in);
        Element root = document.getRootElement();

        //1.端口
        id = Short.parseShort(root.element("id").getText());
        recvPort = Integer.parseInt(root.element("recvport").getText());

        fetchServPort = Integer.parseInt(root.element("fetchservport").getText());
        log.info("GateWay ID:{},Port:{},FetchServPort:{}", id, recvPort, fetchServPort);


        //TODO 数据库连接 连接柜台列表

    }

    public void startup() throws Exception {
        //1.启动TCP服务监听
        initRecv();

        //2.排队机交互
        initFetchServ();

    }


    /**
     * 排队机交互
     * 拍对接 通过这个来取数据
     */
    private void initFetchServ() {
        //设置服务配置
        ServerConfig rpcConfig = new ServerConfig().setPort(fetchServPort).setProtocol("bolt");
        //implement IFetchService 数据提供者配置
        ProviderConfig<IFetchService> providerConfig = new ProviderConfig<IFetchService>()
                //对外暴露的接口 类似dubbo 接口调用
                .setInterfaceId(IFetchService.class.getName())
                //对上面绑定的 IFetchService 进行实现
                .setRef(() -> OrderCmdContainer.getInstance().getAll())
                //绑定配置
                .setServer(rpcConfig);
        providerConfig.export();

        log.info("gateway startup fetchServ success at port : {}", fetchServPort);

    }

    /**
     * 初始化接收端
     */
    private void initRecv() {
        //创建tcp服务端
        NetServer server = vertx.createNetServer();
        //设置链接处理器
        server.connectHandler(new ConnHandler(this));
        //监听端口 端口是否连接成功handler
        server.listen(recvPort, res -> {
            if (res.succeeded()) {
                log.info("gateway startup success at port : {}", recvPort);
            } else {
                log.error("gateway startup fail");
            }
        });
    }
}
