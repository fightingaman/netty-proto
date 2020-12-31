package com.zzm.remoting.protocol;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.zzm.remoting.exception.SerializerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author zzm
 * @version V1.0
 * @Description
 * @date 2018/1/8 11:12
 */
public class HessianSerializer {

    public static byte[] encode(Object obj) throws SerializerException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Hessian2Output hos = new Hessian2Output(baos);
        try {
            hos.writeObject(obj);
            hos.flush();
            hos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SerializerException("Hessian encode error:" + e.getMessage(), e);
        }
    }

    public static <T> T decode(byte[] param, Class<T> clazz) throws SerializerException {
        ByteArrayInputStream bios = new ByteArrayInputStream(param);
        Hessian2Input his = new Hessian2Input(bios);
        try {
            return (T) his.readObject(clazz);
        } catch (IOException e) {
            throw new SerializerException("Hessian decode error " + e.getMessage(), e);
        } finally {
            try {
                his.close();
            } catch (IOException ignored) {
                ;
            }
        }
    }
}
