package com.zzm.remoting.netty;

public class NettyClientConfig {
    /**
     * 工作线程数量
     */
    private int clientWorkerThreads = 4;
    /**
     * 异步callback执行线程数
     */
    private int clientCallbackExecutorThreads = Runtime.getRuntime().availableProcessors();
    /**
     * 异步发送信号量
     */
    private int clientAsyncSemaphoreValue = 256;
    /**
     * 连接超时时间
     */
    private int connectTimeoutMillis = 3000;
    private long channelNotActiveInterval = 1000 * 60;
    private boolean clientOpenSocketHeartBeat = false;
    private int clientChannelMaxIdleTimeSeconds = 120;
    private int clientChannelMaxReadTimeSeconds = 15;
    private int clientChannelMaxWirteTimeSeconds = 15;

    private int clientSocketSndBufSize = 65535;
    private int clientSocketRcvBufSize = 65535;
    private boolean clientCloseSocketIfTimeout = false;

    public boolean isClientCloseSocketIfTimeout() {
        return clientCloseSocketIfTimeout;
    }

    public void setClientCloseSocketIfTimeout(final boolean clientCloseSocketIfTimeout) {
        this.clientCloseSocketIfTimeout = clientCloseSocketIfTimeout;
    }

    public int getClientWorkerThreads() {
        return clientWorkerThreads;
    }

    public void setClientWorkerThreads(int clientWorkerThreads) {
        this.clientWorkerThreads = clientWorkerThreads;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getClientCallbackExecutorThreads() {
        return clientCallbackExecutorThreads;
    }

    public void setClientCallbackExecutorThreads(int clientCallbackExecutorThreads) {
        this.clientCallbackExecutorThreads = clientCallbackExecutorThreads;
    }

    public long getChannelNotActiveInterval() {
        return channelNotActiveInterval;
    }

    public void setChannelNotActiveInterval(long channelNotActiveInterval) {
        this.channelNotActiveInterval = channelNotActiveInterval;
    }

    public int getClientAsyncSemaphoreValue() {
        return clientAsyncSemaphoreValue;
    }

    public void setClientAsyncSemaphoreValue(int clientAsyncSemaphoreValue) {
        this.clientAsyncSemaphoreValue = clientAsyncSemaphoreValue;
    }

    public int getClientChannelMaxIdleTimeSeconds() {
        return clientChannelMaxIdleTimeSeconds;
    }

    public void setClientChannelMaxIdleTimeSeconds(int clientChannelMaxIdleTimeSeconds) {
        this.clientChannelMaxIdleTimeSeconds = clientChannelMaxIdleTimeSeconds;
    }

    public int getClientSocketSndBufSize() {
        return clientSocketSndBufSize;
    }

    public void setClientSocketSndBufSize(int clientSocketSndBufSize) {
        this.clientSocketSndBufSize = clientSocketSndBufSize;
    }

    public int getClientSocketRcvBufSize() {
        return clientSocketRcvBufSize;
    }

    public void setClientSocketRcvBufSize(int clientSocketRcvBufSize) {
        this.clientSocketRcvBufSize = clientSocketRcvBufSize;
    }

    public int getClientChannelMaxReadTimeSeconds() {
        return clientChannelMaxReadTimeSeconds;
    }

    public void setClientChannelMaxReadTimeSeconds(int clientChannelMaxReadTimeSeconds) {
        this.clientChannelMaxReadTimeSeconds = clientChannelMaxReadTimeSeconds;
    }

    public int getClientChannelMaxWirteTimeSeconds() {
        return clientChannelMaxWirteTimeSeconds;
    }

    public void setClientChannelMaxWirteTimeSeconds(int clientChannelMaxWirteTimeSeconds) {
        this.clientChannelMaxWirteTimeSeconds = clientChannelMaxWirteTimeSeconds;
    }

    public boolean isClientOpenSocketHeartBeat() {
        return clientOpenSocketHeartBeat;
    }

    public void setClientOpenSocketHeartBeat(boolean clientOpenSocketHeartBeat) {
        this.clientOpenSocketHeartBeat = clientOpenSocketHeartBeat;
    }
}
