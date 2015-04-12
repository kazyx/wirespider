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
}
