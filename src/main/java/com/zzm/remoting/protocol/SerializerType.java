package com.zzm.remoting.protocol;

public enum SerializerType {
    JSON((byte) 0),
    PROTOSTUFF((byte) 1),
    KYRO((byte) 2),
    Hessian((byte) 3),
    FST((byte)4);

    private byte code;

    SerializerType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static SerializerType valueOf(byte code) {
        for (SerializerType serializerType : SerializerType.values()) {
            if (serializerType.getCode() == code) {
                return serializerType;
            }
        }
        return null;
    }
}
