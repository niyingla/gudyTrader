package thirdpart.bus;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import thirdpart.bean.CommonMsg;
import thirdpart.codec.IMsgCodec;

import java.util.concurrent.TimeUnit;

@Log4j2
@RequiredArgsConstructor
public class MqttBusSender implements IBusSender {

    @NonNull
    private String ip;

    @NonNull
    private int port;

    @NonNull
    private IMsgCodec msgCodec;

    @NonNull
    private Vertx vertx;


    @Override
    public void startup() {
        //连接总线
        mqttConnect();
    }

    /**
     * 初始化链接
     */
    private void mqttConnect() {
        //创建链接类
        MqttClient mqttClient = MqttClient.create(vertx);
        //链接总线
        mqttClient.connect(port, ip, res -> {
            if (res.succeeded()) {
                log.info("connect to mqtt bus[ip:{},port:{}] succeed", ip, port);
                sender = mqttClient;
            } else {
                log.info("connect to mqtt bus[ip:{},port:{}] fail", ip, port);
                //重连
                mqttConnect();
            }
        });

        //连接断开事件处理
        mqttClient.closeHandler(h -> {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (Exception e) {
                log.error(e);
            }
            mqttConnect();
        });
    }

    /////////////////////////////MQTT发送者//////////////////////////
    private volatile MqttClient sender;

    /**
     * 发送总线消息
     * @param msg
     */
    @Override
    public void publish(CommonMsg msg) {
                //发往哪个柜台
        sender.publish(Short.toString(msg.getMsgDst()),
                //消息内容
                msgCodec.encodeToBuffer(msg),
                //保证到达一次
                MqttQoS.AT_LEAST_ONCE,
                //是否重复
                false,
                //是否保存
                false);
    }
}
