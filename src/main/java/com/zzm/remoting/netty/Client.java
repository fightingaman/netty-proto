package com.zzm.remoting.netty;

import com.zzm.remoting.NettyCallback;
import com.zzm.remoting.ResponseFuture;
import com.zzm.remoting.exception.NettyConnectException;
import com.zzm.remoting.exception.NettySendException;
import com.zzm.remoting.exception.NettyTimeoutException;
import com.zzm.remoting.protocol.Packet;

/**
 * @author zzm
 * @version V1.0
 * @Description:
 * @date 2017/12/29 10:31
 */
public interface Client {
    void invokeAsync(final String addr, final Packet packet, final long timeoutMillis, NettyCallback callback) throws InterruptedException, NettyTimeoutException, NettySendException, NettyConnectException;

    ResponseFuture invokeSync(final String addr, final Packet packet, final long timeoutMillis) throws InterruptedException, NettySendException, NettyConnectException, NettyTimeoutException;
}
