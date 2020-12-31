package com.zzm.remoting;

import com.zzm.remoting.netty.NettyOperationHandler;

import java.util.concurrent.ExecutorService;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-28 18:17
 **/
public interface NettyService {
    void start();

    void shutdown();

    void registerRequestHandler(NettyOperationHandler handler, ExecutorService executorService);

    void registerResponseHandler(NettyOperationHandler handler, ExecutorService executorService);

    void registerHeartHandler(NettyOperationHandler handler, ExecutorService executorService);
}
