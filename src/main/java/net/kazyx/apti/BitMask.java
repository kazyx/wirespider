package net.kazyx.apti;

final class BitMask {
    private BitMask() {
    }

    static boolean isMatched(byte data, byte mask) {
        return (data & mask) == mask;
    }

    static byte[] maskAll(byte[] payload, byte[] maskingKey) {
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ maskingKey[i & 3]); // MOD 4
        }
        return payload;
    }

    static final byte BYTE_SYM_0x0F = 0x0F;
    static final byte BYTE_SYM_0x70 = 0x70;
    static final byte BYTE_SYM_0x7E = 0x7E;
    static final byte BYTE_SYM_0x7F = 0x7F;
    static final byte BYTE_SYM_0xFF = (byte) 0xFF;
    static final byte BYTE_SYM_0xFE = (byte) 0xFE;
    static final byte BYTE_SYM_0x80 = (byte) 0x80;
}
