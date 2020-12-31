package com.zzm.remoting.protocol;

import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.Schema;
import com.zzm.remoting.exception.SerializerException;

import java.io.ByteArrayInputStream;

/**
 * @author zzm
 * @version V1.0
 * @date 2018/1/6 12:15
 */
public class ProtostuffSerializer {
    private static final SchemaCache CACHED_SCHEMA = SchemaCache.getInstance();

    private static <T> Schema<T> getSchema(Class<T> cls) {
        return (Schema<T>) CACHED_SCHEMA.get(cls);
    }


    /**
     * 序列化对象
     *
     * @param obj 需要序更列化的对象
     * @return byte []
     * @throws SerializerException
     */
    public static <T> byte[] encode(T obj) throws SerializerException {
        Class<T> cls = (Class<T>) obj.getClass();
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
        try {
            Schema<T> schema = getSchema(cls);
            return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        } catch (Exception e) {
            throw new SerializerException("Protostuff decode error:" + e.getMessage(), e);
        } finally {
            buffer.clear();
        }
    }

    /**
     * 反序列化对象
     *
     * @param param 需要反序列化的byte []
     * @param clazz
     * @return 对象
     * @throws SerializerException
     */
    public static <T> T decode(byte[] param, Class<T> clazz) throws SerializerException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(param);
            Schema<T> schema = getSchema(clazz);
            T object = schema.newMessage();
            ProtostuffIOUtil.mergeFrom(inputStream, object, schema);
            return object;
        } catch (Exception e) {
            throw new SerializerException("Protostuff decode error:" + e.getMessage(), e);
        }
    }
}

