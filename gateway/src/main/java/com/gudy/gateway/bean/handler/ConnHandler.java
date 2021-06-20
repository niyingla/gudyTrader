package com.gudy.gateway.bean.handler;

import com.gudy.gateway.bean.GatewayConfig;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import thirdpart.bean.CommonMsg;

@Log4j2
@RequiredArgsConstructor
public class ConnHandler implements Handler<NetSocket> {


    /**
     * RecordParser https://blog.csdn.net/neosmith/article/details/93724102
     * Vert.x中只有一个二进制协议解析辅助类，即RecordParser，可以很好的解决粘包/拆包问题。它有两种工作模式，
     * 一是delimited mode, 即通过固定分隔符分隔数据包，这种用的其实比较少；
     * 二是fixed size mode，即固定数据长度模式
     */

    @NonNull
    private GatewayConfig config;

    //    包头[ 包体长度 int + 校验和 byte + src short+ dst short + 消息类型 short + 消息状态 byte + 包编号 long ]
    private static final int PACKET_HEADER_LENGTH = 4 + 1 + 2 + 2 + 2 + 1 + 8;


    @Override
    public void handle(NetSocket socket) {

        //传入序列化器
        IMsgHandler msgHandler = new MsgHandler(config.getBodyCodec());
        //调用开启链接方法
        msgHandler.onConnect(socket);


        //1.parser 定义一个固定长度解析器
        final RecordParser parser = RecordParser.newFixed(PACKET_HEADER_LENGTH);

        // 设置处理器
        parser.setOutput(new Handler<Buffer>() {

            //自定义内容
            //    包头[ 包体长度 int + 校验和 byte + src short+ dst short + 消息类型 short + 消息状态 byte + 包编号 long ]
            int bodyLength = -1;
            byte checksum = -1;
            short msgSrc = -1;
            short msgDst = -1;
            short msgType = -1;
            byte status = -1;
            long packetNo = -1;

            /**
             * 此方法会读取多次 第一次 if 进行读取 第二次else 执行读取内容操作
             *
             * @param buffer
             */
            @Override
            public void handle(Buffer buffer) {
                //没有数据 读取数据 -1表示当前还没有长度信息，需要从收到的数据中取出长度
                if (bodyLength == -1) {
                    //读到包头
                    bodyLength = buffer.getInt(0);
                    checksum = buffer.getByte(4);
                    msgSrc = buffer.getShort(5);
                    msgDst = buffer.getShort(7);
                    msgType = buffer.getShort(9);
                    status = buffer.getByte(11);
                    packetNo = buffer.getLong(12);

                    //读满这个长度才回调 当前handle方法
                    parser.fixedSizeMode(bodyLength);
                } else {
                    // 如果size != -1, 说明已经接受到长度信息了，接下来的数据就是protobuf可识别的字节数组
                    byte[] bodyBytes = buffer.getBytes();
                    //组装对象
                    CommonMsg msg;
                    //校验数据长度
                    if (checksum != config.getCs().getChecksum(bodyBytes)) {
                        log.error("illegal byte body exist from client:{}", socket.remoteAddress());
                        return;
                    } else {
                        //判断目标id 和 柜台是否一致
                        if (msgDst != config.getId()) {
                            log.error("recv error msgDst dst: {} from client:{}", msgDst, socket.remoteAddress());
                            return;
                        }

                        msg = new CommonMsg();
                        msg.setBodyLength(bodyBytes.length);
                        msg.setChecksum(checksum);
                        msg.setMsgSrc(msgSrc);
                        msg.setMsgDst(msgDst);
                        msg.setMsgType(msgType);
                        msg.setStatus(status);
                        msg.setMsgNo(packetNo);
                        msg.setBody(bodyBytes);
                        msg.setTimestamp(System.currentTimeMillis());
                        //调用上报柜台方法
                        msgHandler.onCounterData(msg);

                        //reset 等待下一次进入
                        bodyLength = -1;
                        checksum = -1;
                        msgSrc = -1;
                        msgDst = -1;
                        msgType = -1;
                        status = -1;
                        packetNo = -1;
                        parser.fixedSizeMode(PACKET_HEADER_LENGTH);
                    }
                }
            }
        });
        //设置消息处理器
        socket.handler(parser);

        //2.异常 退出 处理器
        socket.closeHandler(close -> {
            msgHandler.onDisConnect(socket);
        });

        socket.exceptionHandler(e -> {
            msgHandler.onException(socket, e);
            socket.close();
        });

    }
}
