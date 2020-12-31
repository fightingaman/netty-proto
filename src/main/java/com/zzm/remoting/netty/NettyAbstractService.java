package com.zzm.remoting.netty;

import com.zzm.remoting.NettyCallback;
import com.zzm.remoting.ResponseFuture;
import com.zzm.remoting.common.RemotingUtil;
import com.zzm.remoting.exception.NettySendException;
import com.zzm.remoting.exception.NettyTimeoutException;
import com.zzm.remoting.protocol.Packet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author zzm
 * @version V1.0
 * @Description
 * @date 2020-12-29 16:01
 **/
public abstract class NettyAbstractService {
    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    protected final ConcurrentMap<Long, ResponseFuture> responseTables = new ConcurrentHashMap<>(1024);
    private final AtomicLong atomicLong = new AtomicLong(0);
    /**
     * 异步
     */
    private final Semaphore semaphore;
    /**
     * 用于处理 requst
     */
    protected NettyOperationHandler nettyRequestHandler;
    protected ExecutorService requestExecutor;
    /**
     * 用于处理 response
     */
    protected NettyOperationHandler nettyResponseHandler;
    protected ExecutorService responseExecutor;
    /**
     * ，如果开启心跳，用于处理心跳，
     */
    protected NettyOperationHandler nettyHeartHandler;
    protected ExecutorService heartExecutor;

    public NettyAbstractService(Integer asyncSemaphore) {
        this.semaphore = new Semaphore(asyncSemaphore, true);
    }

    public abstract ExecutorService getPublicExecutor();

    public void process(ChannelHandlerContext ctx, Packet packet) {
        TransferType transferType = packet.getTransferType();
        switch (transferType) {
            case HEART:
                nettyHeartHandler.executeHandler(ctx, packet, heartExecutor);
                break;
            case REQUEST:
                nettyRequestHandler.executeHandler(ctx, packet, requestExecutor);
                break;
            case RESPONSE:
                nettyResponseHandler.executeHandler(ctx, packet, requestExecutor);
                break;
            default:
                final String remoteAddress = RemotingUtil.parseChannelRemoteAddr(ctx.channel());
                log.warn("netty process can not handle unknown transferType,he channel[{}],packet:{}", remoteAddress, packet.toString());
                break;
        }

    }

    public void scanResponseTable() {
        List<ResponseFuture> rfList = new ArrayList<>();
        Iterator<Map.Entry<Long, ResponseFuture>> it = this.responseTables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ResponseFuture> next = it.next();
            ResponseFuture rep = next.getValue();
            if ((rep.getCurrentTimeMillis() + rep.getTimeoutMillis() + 1000) <= System.currentTimeMillis()) {
                it.remove();
                rfList.add(rep);
                log.warn("remove timeout request" + rep);
            }
        }
        for (ResponseFuture rf : rfList) {
            executeCallBack(rf);
        }
    }

    public void executeCallBack(final ResponseFuture responseFuture) {
        boolean runAsync = true;
        ExecutorService executorService = getPublicExecutor();
        if (executorService != null) {
            try {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        responseFuture.execute();
                    }
                });
            } catch (Exception e) {
                runAsync = false;
                log.error("may be thread is busy", e);
            }
        } else {
            runAsync = false;
        }
        if (!runAsync) {
            responseFuture.execute();
        }
    }

    public void invokeAsyncImpl(Channel channel, Packet packet, long timeoutMillis, NettyCallback callback) throws InterruptedException, NettyTimeoutException, NettySendException, IllegalArgumentException {
        final ResponseFuture responseFuture = new ResponseFuture(timeoutMillis, callback);
        boolean acquire = semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
        if (acquire) {
            final long flag = atomicLong.getAndIncrement();
            packet.setFlag(flag);
            responseTables.put(flag, responseFuture);
            try {
                channel.writeAndFlush(packet).addListener(new GenericFutureListener<Future<? super Void>>() {
                    @Override
                    public void operationComplete(Future<? super Void> future) throws Exception {
                        if (future.isSuccess()) {
                            responseFuture.setSendSuccess(true);
                            return;
                        }
                        responseFuture.setSendSuccess(false);
                        responseFuture.setThrowable(future.cause());
                        responseTables.remove(flag);
                        executeCallBack(responseFuture);
                    }
                });
            } catch (Exception e) {
                log.warn("invokeAsync exception", e);
                throw new NettySendException(RemotingUtil.parseChannelRemoteAddr(channel), e);
            } finally {
                semaphore.release();
            }
        } else {
            String info = String.format("invokeAsync tryAcquire semaphore timeout, %dms, waiting thread nums: %d limitAsyncValue: %d",
                    timeoutMillis,
                    this.semaphore.getQueueLength(),
                    this.semaphore.availablePermits());
            log.warn(info);
            throw new NettyTimeoutException(info);
        }
    }

    public ResponseFuture invokeSyncImpl(Channel channel, Packet packet, long timeoutMillis, NettyCallback callback) throws InterruptedException, NettyTimeoutException, NettySendException, IllegalArgumentException {
        final ResponseFuture responseFuture = new ResponseFuture(timeoutMillis, null);
        final long flag = atomicLong.getAndIncrement();
        packet.setFlag(flag);
        responseTables.put(flag, responseFuture);
        try {
            channel.writeAndFlush(packet).addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) throws Exception {
                    if (future.isSuccess()) {
                        responseFuture.setSendSuccess(true);
                        return;
                    }
                    responseFuture.setSendSuccess(false);
                    responseFuture.setThrowable(future.cause());
                    responseFuture.release();
                    responseTables.remove(flag);
                }
            });
        } catch (Exception e) {
            //不需要执行callback，可以直接remove
            responseTables.remove(flag);
            log.warn("invokeSync exception", e);
            throw new NettySendException(RemotingUtil.parseChannelRemoteAddr(channel), e);
        }
        responseFuture.await();
        if (responseFuture.getPacket() == null) {
            if (responseFuture.isSendSuccess()) {
                log.warn("send message timeout:{}", timeoutMillis);
                throw new NettyTimeoutException(RemotingUtil.parseChannelRemoteAddr(channel));
            } else {
                throw new NettySendException(RemotingUtil.parseChannelRemoteAddr(channel));
            }
        }
        return responseFuture;
    }
}
