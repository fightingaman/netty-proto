package com.zzm.remoting.netty;

import com.zzm.remoting.NettyCallback;
import com.zzm.remoting.NettyService;
import com.zzm.remoting.ResponseFuture;
import com.zzm.remoting.common.RemotingUtil;
import com.zzm.remoting.exception.NettyConnectException;
import com.zzm.remoting.exception.NettySendException;
import com.zzm.remoting.exception.NettyTimeoutException;
import com.zzm.remoting.protocol.Packet;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2018-01-02 9:38
 **/
public class NettyServer extends NettyAbstractService implements NettyService, Server {
    private static Logger log = LoggerFactory.getLogger(NettyServer.class);
    private final ServerBootstrap serverBootstrap;
    private final EventLoopGroup eventLoopGroupSelector;
    private final EventLoopGroup eventLoopGroupBoss;
    private final NettyServerConfig nettyServerConfig;
    private final ExecutorService publicExecutor;
    private DefaultEventExecutorGroup defaultEventExecutorGroup;

    private final ScheduledExecutorService schedule = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        private AtomicInteger index = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "NettyServerResponseTable_" + index.incrementAndGet());
        }
    });

    public NettyServer(NettyServerConfig nettyServerConfig) {
        super(nettyServerConfig.getServerAsyncSemaphoreValue());
        this.nettyServerConfig = nettyServerConfig;

        int threadsNum = nettyServerConfig.getServerCallbackExecutorThreads();
        if (threadsNum <= 0) {
            threadsNum = 4;
        }
        this.publicExecutor = Executors.newFixedThreadPool(threadsNum, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyServerPubExecutor_" + index.incrementAndGet());
            }
        });

        this.eventLoopGroupBoss = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyServerGroupBoss_" + index.incrementAndGet());
            }
        });
        //注册默认handler
        registerHeartHandler(new NettyOperationHandler() {
            @Override
            public void executeHandler(ChannelHandlerContext ctx, Packet packet, ExecutorService executorService) {
                final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
                log.info("netty server : heart, the channel[{}],packet:", remoteAddress, packet.toString());
                packet.setHead("response");
                ctx.writeAndFlush(packet);
            }
        }, null);
        registerRequestHandler(new NettyOperationHandler() {
            @Override
            public void executeHandler(ChannelHandlerContext ctx, Packet packet, ExecutorService executorService) {
                final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
                log.info("netty server : request, the channel[{}],packet:", remoteAddress, packet.toString());
            }
        }, null);
        registerResponseHandler(new NettyOperationHandler() {
            @Override
            public void executeHandler(ChannelHandlerContext ctx, Packet packet, ExecutorService executorService) {
                final long flag = packet.getFlag();
                final ResponseFuture responseFuture = NettyServer.super.responseTables.get(flag);
                if (responseFuture != null) {
                    NettyServer.super.responseTables.remove(flag);
                    responseFuture.setPacket(packet);
                    if (responseFuture.getNettyCallback() != null) {
                        executeCallBack(responseFuture);
                    } else {
                        responseFuture.release();
                    }
                } else {
                    log.warn("receive response, but not matched any request,address:{},packet:{}", RemotingUtil.parseChannelRemoteAddr(ctx.channel()), packet.toString());
                }
            }
        }, null);
        if (useEpoll()) {
            this.eventLoopGroupSelector = new EpollEventLoopGroup(nettyServerConfig.getServerSelectorThreads(), new ThreadFactory() {
                private AtomicInteger index = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, String.format("NettyServerGroupEpollSelector_%d", index.incrementAndGet()));
                }
            });
        } else {
            this.eventLoopGroupSelector = new NioEventLoopGroup(nettyServerConfig.getServerSelectorThreads(), new ThreadFactory() {
                private AtomicInteger index = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, String.format("NettyServerGroupNioSelector_%d", index.incrementAndGet()));
                }
            });
        }
        this.serverBootstrap = new ServerBootstrap();
    }

    private boolean useEpoll() {
        return RemotingUtil.isLinuxPlatform()
                && nettyServerConfig.isUseEpollNativeSelector()
                && Epoll.isAvailable();
    }

    @Override
    public ExecutorService getPublicExecutor() {
        return publicExecutor;
    }

    @Override
    public void invokeAsync(Channel channel, Packet packet, long timeoutMillis, NettyCallback callback) throws InterruptedException, NettyTimeoutException, NettySendException, NettyConnectException {
        this.invokeAsyncImpl(channel, packet, timeoutMillis, callback);
    }

    @Override
    public ResponseFuture invokeSync(Channel channel, Packet packet, long timeoutMillis) throws InterruptedException, NettySendException, NettyConnectException, NettyTimeoutException {
        return this.invokeSyncImpl(channel, packet, timeoutMillis, null);
    }

    @Override
    public void registerRequestHandler(NettyOperationHandler handler, ExecutorService executorService) {
        if (executorService == null) {
            this.requestExecutor = this.publicExecutor;
        } else {
            this.requestExecutor = executorService;
        }
        this.nettyRequestHandler = handler;
    }

    @Override
    public void registerResponseHandler(NettyOperationHandler handler, ExecutorService executorService) {
        if (executorService == null) {
            this.responseExecutor = this.publicExecutor;
        } else {
            this.responseExecutor = executorService;
        }
        this.nettyResponseHandler = handler;
    }

    @Override
    public void registerHeartHandler(NettyOperationHandler handler, ExecutorService executorService) {
        if (executorService == null) {
            this.heartExecutor = this.publicExecutor;
        } else {
            this.heartExecutor = executorService;
        }
        this.nettyHeartHandler = handler;
    }

    @Override
    public void start() {
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(
                nettyServerConfig.getServerWorkerThreads(),
                new ThreadFactory() {
                    private AtomicInteger index = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, String.format("NettyServerWorkerThread_%d", index.incrementAndGet()));
                    }
                });

        ServerBootstrap childHandler =
                this.serverBootstrap.group(this.eventLoopGroupBoss, this.eventLoopGroupSelector)
                        .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 1024)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.SO_KEEPALIVE, false)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childOption(ChannelOption.SO_SNDBUF, nettyServerConfig.getServerSocketSndBufSize())
                        .childOption(ChannelOption.SO_RCVBUF, nettyServerConfig.getServerSocketRcvBufSize())
                        .localAddress(new InetSocketAddress(this.nettyServerConfig.getListenPort()))
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(
                                        defaultEventExecutorGroup,
                                        new NettyEncoder(),
                                        new NettyDecoder(),
                                        new IdleStateHandler(0, 0, nettyServerConfig.getServerChannelMaxIdleTimeSeconds()),
                                        new NettyConnectManageHandler(),
                                        new NettyServerHandler());
                            }
                        });

        if (nettyServerConfig.isServerPooledByteBufAllocatorEnable()) {
            childHandler.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        }

        try {
            ChannelFuture sync = this.serverBootstrap.bind().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException("server bind error:", e);
        }

        this.schedule.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                scanResponseTable();
            }
        }, 3, 1, TimeUnit.SECONDS);
    }

    @Override
    public void shutdown() {
        this.eventLoopGroupBoss.shutdownGracefully();
        this.eventLoopGroupSelector.shutdownGracefully();
        this.defaultEventExecutorGroup.shutdownGracefully();
        this.publicExecutor.shutdown();
        this.schedule.shutdown();
        if (super.requestExecutor != null) {
            super.requestExecutor.shutdown();
        }
        if (super.responseExecutor != null) {
            super.responseExecutor.shutdown();
        }
        if (super.heartExecutor != null) {
            super.heartExecutor.shutdown();
        }
    }

    class NettyServerHandler extends SimpleChannelInboundHandler<Packet> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
            process(ctx, packet);
        }
    }

    class NettyConnectManageHandler extends ChannelDuplexHandler {
        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("netty server pipeline: channelRegistered {}", remoteAddress);
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("netty server pipeline: channelUnregistered, the channel[{}]", remoteAddress);
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("netty server pipeline: channelActive, the channel[{}]", remoteAddress);
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("netty server pipeline: channelInactive, the channel[{}]", remoteAddress);
            super.channelInactive(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
                    log.warn("netty server pipeline: IDLE exception [{}]", remoteAddress);
                    RemotingUtil.closeChannel(ctx.channel());
                }
            }

            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
            log.warn("netty server pipeline: exceptionCaught {}", remoteAddress);
            log.warn("netty server pipeline: exceptionCaught exception.", cause);
            RemotingUtil.closeChannel(ctx.channel());
        }
    }
}
