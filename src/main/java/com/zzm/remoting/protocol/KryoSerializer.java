package com.zzm.remoting.protocol;


import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.zzm.remoting.exception.SerializerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author zzm
 * @version V1.0
 * @Description
 * @date 2018/1/8 11:17
 */
public class KryoSerializer {

    public static byte[] encode(Object obj) throws SerializerException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Kryo kryo = new Kryo();
        Output output = new Output(outputStream);
        try {
            kryo.writeObject(output, obj);
            output.flush();
            output.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new SerializerException("kryo encode error:" + e.getMessage(), e);
        }
    }


    public static <T> T decode(byte[] param, Class<T> clazz) throws SerializerException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(param);
        Kryo kryo = new Kryo();
        Input input = new Input(inputStream);
        try {
            return kryo.readObject(input, clazz);
        } catch (Exception e) {
            throw new SerializerException("kryo decode error:" + e.getMessage(), e);
        } finally {
            input.close();
        }
    }
}
