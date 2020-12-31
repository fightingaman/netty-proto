package com.zzm.remoting.netty;

import com.zzm.remoting.NettyCallback;
import com.zzm.remoting.ResponseFuture;
import com.zzm.remoting.exception.NettyConnectException;
import com.zzm.remoting.exception.NettySendException;
import com.zzm.remoting.exception.NettyTimeoutException;
import com.zzm.remoting.protocol.Packet;
import io.netty.channel.Channel;


public interface Server {
    void invokeAsync(final Channel channel, final Packet packet, final long timeoutMillis, NettyCallback callback) throws InterruptedException, NettyTimeoutException, NettySendException, NettyConnectException;

    ResponseFuture invokeSync(final Channel channel, final Packet packet, final long timeoutMillis) throws InterruptedException, NettySendException, NettyConnectException, NettyTimeoutException;
}
