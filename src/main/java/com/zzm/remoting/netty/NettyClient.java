package com.zzm.remoting.netty;

import com.zzm.remoting.NettyCallback;
import com.zzm.remoting.NettyService;
import com.zzm.remoting.ResponseFuture;
import com.zzm.remoting.common.RemotingUtil;
import com.zzm.remoting.exception.NettyConnectException;
import com.zzm.remoting.exception.NettySendException;
import com.zzm.remoting.exception.NettyTimeoutException;
import com.zzm.remoting.protocol.Packet;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-28 18:35
 **/
public class NettyClient extends NettyAbstractService implements NettyService, Client {
    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    private static final long LOCK_TIMEOUT_MILLIS = 3000;

    private final NettyClientConfig nettyClientConfig;
    private final Bootstrap bootstrap = new Bootstrap();
    private final EventLoopGroup eventLoopGroupWorker;
    private final Lock lockChannelTables = new ReentrantLock();

    private final ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        private AtomicInteger threadIndex = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "NettyClientResponseTablesExecutor_" + this.threadIndex.incrementAndGet());
        }
    });
    private final ConcurrentMap<String, ChannelFuture> channelTables = new ConcurrentHashMap<>(32);

    private final ExecutorService publicExecutor;
    private DefaultEventExecutorGroup defaultEventExecutorGroup;


    public NettyClient(NettyClientConfig clientConfig) {
        super(clientConfig.getClientAsyncSemaphoreValue());
        this.nettyClientConfig = clientConfig;
        int threads = clientConfig.getClientCallbackExecutorThreads();
        if (threads <= 0) {
            threads = 4;
        }
        this.publicExecutor = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyClientPublicExecutor_" + index.incrementAndGet());
            }
        });
        this.eventLoopGroupWorker = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "NettyClientSelector_" + index.incrementAndGet());
            }
        });
        //注册心跳handler
        registerHeartHandler(new NettyOperationHandler() {
            @Override
            public void executeHandler(ChannelHandlerContext ctx, Packet packet, ExecutorService executorService) {
                final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
                log.info("netty client : heart, the channel[{}],packet:", remoteAddress, packet.toString());
            }
        }, null);
        registerRequestHandler(new NettyOperationHandler() {
            @Override
            public void executeHandler(ChannelHandlerContext ctx, Packet packet, ExecutorService executorService) {
                final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
                log.info("netty client : request, the channel[{}],packet:", remoteAddress, packet.toString());
            }
        }, null);
        registerResponseHandler(new NettyOperationHandler() {
            @Override
            public void executeHandler(ChannelHandlerContext ctx, Packet packet, ExecutorService executorService) {
                final long flag = packet.getFlag();
                final ResponseFuture responseFuture = NettyClient.super.responseTables.get(flag);
                if (responseFuture != null) {
                    NettyClient.super.responseTables.remove(flag);
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
    }


    @Override
    public void start() {
        this.defaultEventExecutorGroup = new DefaultEventExecutorGroup(nettyClientConfig.getClientWorkerThreads(), new ThreadFactory() {
            private AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("NettyClientWorkerThread_%d", this.threadIndex.incrementAndGet()));
            }
        });
        this.bootstrap.group(this.eventLoopGroupWorker).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyClientConfig.getConnectTimeoutMillis())
                .option(ChannelOption.SO_RCVBUF, nettyClientConfig.getClientSocketRcvBufSize())
                .option(ChannelOption.SO_SNDBUF, nettyClientConfig.getClientSocketSndBufSize())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(
                                defaultEventExecutorGroup,
                                new NettyEncoder(),
                                new NettyDecoder(),
                                new IdleStateHandler(nettyClientConfig.getClientChannelMaxReadTimeSeconds(), nettyClientConfig.getClientChannelMaxWirteTimeSeconds(), nettyClientConfig.getClientChannelMaxIdleTimeSeconds()),
                                new NettyConnectHandler(),
                                new NettyClientHandler());
                    }
                });
        this.scheduled.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                scanResponseTable();
            }
        }, 5, 1, TimeUnit.SECONDS);
    }

    class NettyClientHandler extends SimpleChannelInboundHandler<Packet> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
            process(ctx, packet);
        }
    }

    class NettyConnectHandler extends ChannelDuplexHandler {
        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                            ChannelPromise promise) throws Exception {
            final String local = localAddress == null ? "unknown" : RemotingUtil.parseSocketAddressAddr(localAddress);
            final String remote = remoteAddress == null ? "unknown" : RemotingUtil.parseSocketAddressAddr(remoteAddress);
            log.info("netty client pipeline: CONNECT  {} => {}", local, remote);

            super.connect(ctx, remoteAddress, localAddress, promise);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("netty client pipeline: DISCONNECT {}", remoteAddress);
            closeChannel(ctx.channel());
            super.disconnect(ctx, promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
            log.info("netty client pipeline: CLOSE {}", remoteAddress);
            closeChannel(ctx.channel());
            super.close(ctx, promise);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.READER_IDLE) || event.state().equals(IdleState.WRITER_IDLE)) {
                    final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
                    log.warn("netty client pipeline: READER_IDLE or WRITER_IDLE [{}]", remoteAddress);
                    if (nettyClientConfig.isClientOpenSocketHeartBeat()) {
                        Packet packet = new Packet("request");
                        packet.setTransferType(TransferType.HEART);
                        ctx.writeAndFlush(packet);
                    }
                }
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
                    log.warn("netty client pipeline: IDLE exception [{}]", remoteAddress);
                    closeChannel(ctx.channel());
                    ctx.fireUserEventTriggered(evt);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
            log.warn("netty client pipeline: exceptionCaught {}", remoteAddress);
            log.warn("netty client pipeline: exceptionCaught exception", cause);
            closeChannel(ctx.channel());
        }
    }

    @Override
    public void shutdown() {
        this.defaultEventExecutorGroup.shutdownGracefully();
        this.eventLoopGroupWorker.shutdownGracefully();
        this.scheduled.shutdown();
        this.publicExecutor.shutdown();
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

    @Override
    public void registerRequestHandler(NettyOperationHandler handler, ExecutorService executorService) {
        if (executorService == null) {
            super.requestExecutor = this.publicExecutor;
        } else {
            super.requestExecutor = executorService;
        }
        super.nettyRequestHandler = handler;
    }

    @Override
    public void registerResponseHandler(NettyOperationHandler handler, ExecutorService executorService) {
        if (executorService == null) {
            super.responseExecutor = this.publicExecutor;
        } else {
            super.responseExecutor = executorService;
        }
        super.nettyResponseHandler = handler;
    }

    @Override
    public void registerHeartHandler(NettyOperationHandler handler, ExecutorService executorService) {
        if (executorService == null) {
            super.heartExecutor = this.publicExecutor;
        } else {
            super.heartExecutor = executorService;
        }
        super.nettyHeartHandler = handler;
    }

    @Override
    public ExecutorService getPublicExecutor() {
        return publicExecutor;
    }

    public void closeChannel(Channel channel) {
        if (channel == null) {
            return;
        }
        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    String address = null;
                    ChannelFuture channelFuture = null;
                    boolean removeChannel = true;
                    for (Map.Entry<String, ChannelFuture> entry : channelTables.entrySet()) {
                        if (channel.equals(entry.getValue().channel())) {
                            address = entry.getKey();
                            channelFuture = entry.getValue();
                            break;
                        }
                    }
                    if (channelFuture == null) {
                        removeChannel = false;
                        log.info("channel has been removed:{}", RemotingUtil.parseChannelRemoteAddr(channel));
                    }
                    if (removeChannel) {
                        this.channelTables.remove(address);
                        RemotingUtil.closeChannel(channel);
                    }
                } catch (Exception e) {
                    log.error("closeChannel exception", e);
                } finally {
                    this.lockChannelTables.unlock();
                }
            } else {
                log.warn("closeChannel:timeout, {}ms", LOCK_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            log.error("closeChannel exception", e);
        }
    }

    @Override
    public ResponseFuture invokeSync(String addr, Packet packet, long timeoutMillis) throws IllegalArgumentException
            , InterruptedException, NettySendException
            , NettyConnectException, NettyTimeoutException {
        if (addr == null) {
            throw new IllegalArgumentException("addr can not be empty");
        }
        Channel channel = getOrCreateChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                return this.invokeSyncImpl(channel, packet, timeoutMillis, null);
            } catch (NettySendException e) {
                this.closeChannel(channel);
                log.info("send failure,close channel,addr:{}", addr);
                throw e;
            }
        } else {
            this.closeChannel(channel);
            log.info("connect failure,close channel,addr:{}", addr);
            throw new NettyConnectException(addr);
        }
    }

    @Override
    public void invokeAsync(String addr, Packet packet, long timeoutMillis, NettyCallback callback) throws InterruptedException
            , NettyTimeoutException, NettySendException
            , IllegalArgumentException, NettyConnectException {
        if (addr == null) {
            throw new IllegalArgumentException("addr can not be empty");
        }
        Channel channel = getOrCreateChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                this.invokeAsyncImpl(channel, packet, timeoutMillis, callback);
            } catch (NettySendException e) {
                this.closeChannel(channel);
                log.info("send failure,close channel,addr:{}", addr);
                throw e;
            }
        } else {
            this.closeChannel(channel);
            log.info("connect failure,close channel,addr:{}", addr);
            throw new NettyConnectException(addr);
        }
    }

    public Channel getOrCreateChannel(String addr) {
        ChannelFuture future = this.channelTables.get(addr);
        if (future != null && future.channel() != null && future.channel().isActive()) {
            return future.channel();
        }
        return create(addr);
    }

    public Channel create(String addr) {
        ChannelFuture future = this.channelTables.get(addr);
        if (future != null && future.channel() != null && future.channel().isActive()) {
            return future.channel();
        }
        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    future = this.channelTables.get(addr);
                    if (future != null && future.channel() != null && future.channel().isActive()) {
                        return future.channel();
                    }
                    future = this.bootstrap.connect(RemotingUtil.string2SocketAddress(addr));
                    log.info("createChannel: begin to connect remote host[{}] asynchronously", addr);
                    this.channelTables.put(addr, future);
                } catch (Exception e) {
                    log.error("createChannel: create channel exception", e);
                } finally {
                    lockChannelTables.unlock();
                }
            } else {
                log.warn("createChannel:timeout, {}ms", LOCK_TIMEOUT_MILLIS);
            }
            if (future != null) {
                if (future.awaitUninterruptibly(this.nettyClientConfig.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                    if (future.channel() != null && future.channel().isActive()) {
                        return future.channel();
                    } else {
                        log.warn("createChannel: connect remote host[{}] failed, " + future.toString(), addr, future.cause());
                    }
                } else {
                    log.warn("connect remote host[{}] timeout{}ms", addr, this.nettyClientConfig.getConnectTimeoutMillis());
                }
            }
        } catch (InterruptedException e) {
            log.error("createChannel exception", e);
        }
        return null;
    }
}
