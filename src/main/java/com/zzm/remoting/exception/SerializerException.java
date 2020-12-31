package com.zzm.remoting.exception;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2018-01-06 10:06
 **/
public class SerializerException extends Exception {
    public SerializerException(String message) {
        this(message, null);
    }

    public SerializerException(String message, Throwable cause) {
        super(message, cause);
    }
}
