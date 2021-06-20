package thirdpart.codec;

import io.vertx.core.buffer.Buffer;
import thirdpart.bean.CommonMsg;

public interface IMsgCodec {


    //    包头[ 包体长度 int + 校验和 byte + src short+ dst short + 消息类型 short + 消息状态 byte + 包编号 long ]
    //    包体[ 数据 byte[] ]
    default byte[] encodeToByteArr(CommonMsg msg) {
        return encodeToBuffer(msg).getBytes();
    }

    Buffer encodeToBuffer(CommonMsg msg);

    CommonMsg decodeFromBufferNoCsCheck(Buffer buffer);

}
