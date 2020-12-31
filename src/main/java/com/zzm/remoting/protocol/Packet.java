package com.zzm.remoting.protocol;

import com.zzm.remoting.netty.TransferType;
import com.zzm.remoting.exception.SerializerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * @author
 * @version V1.0
 * @Description
 * @date 2020-12-28 19:11
 **/
public class Packet implements Serializable {
    private long flag;
    private static Logger log = LoggerFactory.getLogger(Packet.class);
    private SerializerType serializerType = SerializerType.JSON;
    private TransferType transferType = TransferType.REQUEST;
    private Object head;
    private transient byte[] body;
    private String error;

    public Packet() {
    }

    public Packet(Object head) {
        this(head, SerializerType.JSON);
    }

    public Packet(Object head, TransferType transferType) {
        this.transferType = transferType;
        this.head = head;
    }

    public Packet(Object head, SerializerType serializerType) {
        this.serializerType = serializerType;
        this.head = head;
    }

    public ByteBuffer encode() {
        int length = 4;
        byte[] head = null;
        try {
            head = this.serializer();
        } catch (SerializerException e) {
            log.warn("serializer exception", e);
        }
        int headLength = head == null ? 0 : head.length;
        int dataLength = body == null ? 0 : body.length;
        //标记序列化协议
        int serializer = 1;
        length += headLength;
        length += dataLength;
        length += serializer;
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 1 + length - dataLength);
        byteBuffer.putInt(length);
        byteBuffer.put(serializerType.getCode());
        byteBuffer.putInt(headLength);
        if (head != null) {
            byteBuffer.put(head);
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    /**
     * 数据协议结构 数据总长度(int)+序列化标识(byte)+实体序列化后字节长度(int)+实体序列化字节数组(byte[])
     *
     * @param byteBuffer
     * @return
     */
    public static Packet decode(ByteBuffer byteBuffer) {
        int length = byteBuffer.limit();
        byte serializerType = byteBuffer.get();
        int headLength = byteBuffer.getInt();
        byte[] head = new byte[headLength];
        byteBuffer.get(head);
        Packet packet = null;
        try {
            packet = dserializer(head, SerializerType.valueOf(serializerType));
            int dataLength = length - 1 - 4 - headLength;
            if (dataLength > 0) {
                byte[] data = new byte[dataLength];
                byteBuffer.get(data);
                packet.setBody(data);
            }
        } catch (SerializerException e) {
            log.error("decode serializer exception", e);
        }
        return packet;
    }

    public byte[] serializer() throws SerializerException {
        byte[] head = null;
        if (SerializerType.PROTOSTUFF == this.serializerType) {
            head = ProtostuffSerializer.encode(this);
        } else if (SerializerType.KYRO == this.serializerType) {
            head = KryoSerializer.encode(this);
        } else if (SerializerType.Hessian == this.serializerType) {
            head = HessianSerializer.encode(this);
        } else if (SerializerType.FST == this.serializerType) {
            head = FastSerializer.encode(this);
        } else {
            head = JsonSerializer.encode(this);
        }
        return head;
    }

    public static Packet dserializer(byte[] head, SerializerType serializerType) throws SerializerException {
        Packet packet = null;
        if (SerializerType.PROTOSTUFF == serializerType) {
            packet = ProtostuffSerializer.decode(head, Packet.class);
        } else if (SerializerType.KYRO == serializerType) {
            packet = KryoSerializer.decode(head, Packet.class);
        } else if (SerializerType.Hessian == serializerType) {
            packet = HessianSerializer.decode(head, Packet.class);
        } else if (SerializerType.FST == serializerType) {
            packet = FastSerializer.decode(head, Packet.class);
        } else {
            packet = JsonSerializer.decode(head, Packet.class);
        }
        return packet;
    }

    public long getFlag() {
        return flag;
    }

    public void setFlag(long flag) {
        this.flag = flag;
    }

    public SerializerType getSerializerType() {
        return serializerType;
    }

    public void setSerializerType(SerializerType serializerType) {
        this.serializerType = serializerType;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public Object getHead() {
        return head;
    }

    public void setHead(Object head) {
        this.head = head;
    }

    public TransferType getTransferType() {
        return transferType;
    }

    public void setTransferType(TransferType transferType) {
        this.transferType = transferType;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "flag=" + flag +
                "serializerType=" + serializerType +
                ", transferType=" + transferType +
                ", head=" + head +
                '}';

    }
}
