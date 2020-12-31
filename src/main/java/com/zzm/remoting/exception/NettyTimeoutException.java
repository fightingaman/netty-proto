package com.zzm.remoting.exception;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-29 11:30
 **/
public class NettyTimeoutException extends Exception{
    private static final long serialVersionUID = 4106899185095245979L;

    public NettyTimeoutException(String message) {
        super(message);
    }

    public NettyTimeoutException(String addr, long timeoutMillis) {
        this(addr, timeoutMillis, null);
    }

    public NettyTimeoutException(String addr, long timeoutMillis, Throwable cause) {
        super("timeout on the channel <" + addr + "> timeout, " + timeoutMillis + "(ms)", cause);
    }
}
