package com.zzm.remoting.netty;

import com.zzm.remoting.common.RemotingUtil;
import com.zzm.remoting.protocol.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-28 19:10
 **/
public class NettyEncoder extends MessageToByteEncoder<Packet> {
    private static final Logger log = LoggerFactory.getLogger(NettyEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Packet packet, ByteBuf byteBuf) throws Exception {
        try {
            ByteBuffer byteBuffer = packet.encode();
            byteBuf.writeBytes(byteBuffer);
            if (packet.getBody() != null) {
                byteBuf.writeBytes(packet.getBody());
            }
        } catch (Exception e) {
            log.error("encode exception, " + RemotingUtil.parseChannelRemoteAddr(channelHandlerContext.channel()), e);
        }
    }
}
