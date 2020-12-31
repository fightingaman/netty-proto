package com.zzm.remoting;

import com.zzm.remoting.netty.*;
import com.zzm.remoting.protocol.*;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2018-01-02 10:35
 **/
public class Test {
    private static Client client;
    private static Server server;

    @Before
    public void createServer() throws Exception {
        final NettyServer nettyServer = new NettyServer(new NettyServerConfig());
        nettyServer.registerRequestHandler(new NettyOperationHandler() {
            @Override
            public void executeHandler(ChannelHandlerContext ctx, Packet packet, ExecutorService executorService) {
                System.out.println(packet.toString());
                Packet packet_ = new Packet("黄河收到", TransferType.RESPONSE);
                ctx.writeAndFlush(packet_);
                return;
            }
        }, Executors.newFixedThreadPool(20));
        nettyServer.start();
        server = nettyServer;
    }

    @Before
    public void createClient() {
        NettyClientConfig nettyClientConfig = new NettyClientConfig();
        nettyClientConfig.setClientOpenSocketHeartBeat(true);
        NettyClient nettyClient = new NettyClient(nettyClientConfig);
        nettyClient.registerResponseHandler(new NettyOperationHandler() {
            @Override
            public void executeHandler(ChannelHandlerContext ctx, Packet packet, ExecutorService executorService) {
                System.out.println(packet.toString());
                Packet packet_ = new Packet("长江收到", TransferType.REQUEST);
                ctx.writeAndFlush(packet_);
                return;
            }
        }, null);
        nettyClient.start();
        client = nettyClient;
    }


    @org.junit.Test
    public void test() throws Exception {
        Packet packet = new Packet("666", SerializerType.Hessian);
        client.invokeAsync("127.0.0.1:9999", packet, 1000 * 30, responseFuture -> {
            System.out.println(responseFuture.getPacket().toString());
        });
        new Semaphore(0).acquire();
    }
}
