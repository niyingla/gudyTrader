package thirdpart.codec;


import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;

public class BodyCodec implements IBodyCodec {

    @Override
    public byte[] serialize(Object data) throws CodecException {
        return SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(data);
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clz) throws CodecException {
        return SerializerManager.getSerializer(SerializerManager.Hessian2).deserialize(data, clz.getName());
    }
}
