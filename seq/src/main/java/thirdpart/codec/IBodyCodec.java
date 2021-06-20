package thirdpart.codec;


import com.alipay.remoting.exception.CodecException;

public interface IBodyCodec {

    <T> byte[] serialize(T obj) throws  CodecException;

    <T> T deserialize(byte[] bytes, Class<T> clazz) throws  CodecException;
}
