package com.zzm.remoting;

import com.zzm.remoting.protocol.Packet;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-29 10:36
 **/
public class ResponseFuture {
    private NettyCallback nettyCallback;
    private volatile boolean sendSuccess = true;
    private CountDownLatch countDownLatch = new CountDownLatch(1);
    private final long currentTimeMillis = System.currentTimeMillis();
    private final long timeoutMillis;
    private Throwable throwable;
    private Packet packet;
    /**
     * 保证callback执行一次
     */
    private AtomicBoolean isCallBack = new AtomicBoolean(false);

    public ResponseFuture(long timeoutMillis, NettyCallback nettyCallback) {
        this.timeoutMillis = timeoutMillis;
        this.nettyCallback = nettyCallback;
    }

    public void execute() {
        if (nettyCallback != null) {
            if (isCallBack.compareAndSet(false, true)) {
                nettyCallback.callback(this);
            }
        }
    }

    public ResponseFuture await() throws InterruptedException {
        countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        return this;
    }

    public void release() {
        countDownLatch.countDown();
    }

    public boolean isSendSuccess() {
        return sendSuccess;
    }

    public void setSendSuccess(boolean sendSuccess) {
        this.sendSuccess = sendSuccess;
    }

    public long getCurrentTimeMillis() {
        return currentTimeMillis;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Packet getPacket() {
        return packet;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    public NettyCallback getNettyCallback() {
        return nettyCallback;
    }

    public void setNettyCallback(NettyCallback nettyCallback) {
        this.nettyCallback = nettyCallback;
    }
}
