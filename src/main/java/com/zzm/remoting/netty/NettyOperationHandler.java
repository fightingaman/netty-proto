package com.zzm.remoting.netty;

import com.zzm.remoting.protocol.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.ExecutorService;

/**
 * @author zzm
 * @version V1.0
 * @Description
 * @date 2018-01-03 16:08
 **/
public interface NettyOperationHandler {
    void executeHandler(final ChannelHandlerContext ctx, final Packet packet, final ExecutorService executorService);
}
