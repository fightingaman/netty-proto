package com.zzm.remoting.protocol;

import com.zzm.remoting.exception.SerializerException;
import org.nustaq.serialization.FSTConfiguration;

public class FastSerializer {
    private static FSTConfiguration configuration = FSTConfiguration.createDefaultConfiguration();

    public static byte[] encode(Object obj) throws SerializerException {
        try {
            return configuration.asByteArray(obj);
        } catch (Exception e) {
            throw new SerializerException("fast encode error:" + e.getMessage(), e);
        }
    }

    public static <T> T decode(byte[] bytes, Class<T> tClass) throws SerializerException {
        try {
            return tClass.cast(configuration.asObject(bytes));
        } catch (Exception e) {
            throw new SerializerException("fast decode error:" + e.getMessage(), e);
        }
    }
}
