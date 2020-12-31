package com.zzm.remoting.exception;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-29 11:50
 **/
public class NettySendException extends Exception {
    private static final long serialVersionUID = 4106899185095245979L;

    public NettySendException(String addr) {
        this(addr, null);
    }

    public NettySendException(String addr, Throwable cause) {
        super("send message the channel <" + addr + "> exception", cause);
    }
}
