package com.zzm.remoting.netty;


/**
 *
 */
public enum TransferType {
    /**
     * 心跳
     */
    HEART((byte) 0),
    REQUEST((byte) 1),
    RESPONSE((byte) 2);

    private byte code;

    TransferType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static TransferType valueOf(byte code) {
        for (TransferType transferType : TransferType.values()) {
            if (transferType.getCode() == code) {
                return transferType;
            }
        }
        return null;
    }
}
