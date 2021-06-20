package com.gudy.gateway;

import com.gudy.gateway.bean.GatewayConfig;
import lombok.extern.log4j.Log4j2;
import thirdpart.checksum.ByteCheckSum;
import thirdpart.codec.BodyCodec;

import java.io.FileInputStream;
import java.io.InputStream;

@Log4j2
public class GatewayStartup {
    public static void main(String[] args) throws Exception {
        String configFileName = "gateway.xml";

        GatewayConfig config = new GatewayConfig();

        //输入流
        InputStream inputStream;
        try {
            //用户目录 就是resources
            inputStream = new FileInputStream(System.getProperty("user.dir") + "\\" + configFileName);
            log.info("gateway.xml exist in jar path");
        } catch (Exception e) {
            inputStream = GatewayStartup.class.getResourceAsStream("/" + configFileName);
            log.info("gateway.xml exist in jar file");
        }

        //加载配置
        config.initConfig(inputStream);
//        config.initConfig(GatewayStartup.class.getResource("/").getPath()
//                + configFileName);
        //设置检验类
        config.setCs(new ByteCheckSum());
        //设置序列化类
        config.setBodyCodec(new BodyCodec());
        //启动服务
        config.startup();
    }
}
