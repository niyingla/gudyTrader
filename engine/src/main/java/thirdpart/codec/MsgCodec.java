package thirdpart.codec;

import io.vertx.core.buffer.Buffer;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import thirdpart.bean.CommonMsg;

@Log4j2
@AllArgsConstructor
public class MsgCodec implements IMsgCodec {

    //    包头[ 包体长度 int + 校验和 byte + src short+ dst short + 消息类型 short + 消息状态 byte + 包编号 long ]
    //    包体[ 数据 byte[] ]

    public Buffer encodeToBuffer(CommonMsg msg) {
        return Buffer.buffer().appendInt(msg.getBodyLength())
                .appendByte(msg.getChecksum())
                .appendShort(msg.getMsgSrc())
                .appendShort(msg.getMsgDst())
                .appendShort(msg.getMsgType())
                .appendByte(msg.getStatus())
                .appendLong(msg.getMsgNo())
                .appendBytes(msg.getBody());
    }


    public CommonMsg decodeFromBufferNoCsCheck(Buffer buffer) {

        int bodyLength = buffer.getInt(0);
        byte checksum = buffer.getByte(4);
        short msgSrc = buffer.getShort(5);
        short msgDst = buffer.getShort(7);
        short msgType = buffer.getShort(9);
        byte status = buffer.getByte(11);
        long packetNo = buffer.getLong(12);
        byte[] bodyBytes = buffer.getBytes(20, 20 + bodyLength);

        CommonMsg msg = new CommonMsg();
        //判断校验和 防止传输错误和篡改
        msg.setBodyLength(bodyBytes.length);
        msg.setChecksum(checksum);
        msg.setMsgSrc(msgSrc);
        msg.setMsgDst(msgDst);
        msg.setMsgType(msgType);
        msg.setStatus(status);
        msg.setBody(bodyBytes);
        msg.setMsgNo(packetNo);

        return msg;

    }


}