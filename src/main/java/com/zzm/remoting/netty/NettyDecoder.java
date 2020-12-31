package com.zzm.remoting.netty;

import com.zzm.remoting.common.RemotingUtil;
import com.zzm.remoting.protocol.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-28 19:26
 **/
public class NettyDecoder extends LengthFieldBasedFrameDecoder {
    private static final Logger log = LoggerFactory.getLogger(NettyDecoder.class);

    private static final int FRAME_MAX_LENGTH = Integer.MAX_VALUE;

    public NettyDecoder() {
        super(FRAME_MAX_LENGTH, 0, 4, 0, 4);
    }

    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf byteBuf = null;
        try {
            byteBuf = (ByteBuf) super.decode(ctx, in);
            if (byteBuf == null) {
                return null;
            }
            ByteBuffer byteBuffer = byteBuf.nioBuffer();
            return Packet.decode(byteBuffer);
        } catch (Exception e) {
            log.error("decode exception, " + RemotingUtil.parseChannelRemoteAddr(ctx.channel()), e);
        } finally {
            if (null != byteBuf) {
                byteBuf.release();
            }
        }
        return null;
    }
}
