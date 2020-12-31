package com.zzm.remoting.exception;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-29 16:33
 **/
public class NettyConnectException extends Exception {

    public NettyConnectException(String addr) {
        this(addr, null);
    }

    public NettyConnectException(String addr, Throwable cause) {
        super("can not connect channel <" + addr + "> exception", cause);
    }
}
