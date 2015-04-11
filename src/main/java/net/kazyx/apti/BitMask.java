package net.kazyx.apti;

final class BitMask {
    private BitMask() {
    }

    static boolean isMatch(byte data, int mask) {
        return (data & mask) == mask;
    }

    static byte[] mask(byte[] payload, byte[] mask, int offset) {
        for (int i = 0; i < payload.length - offset; i++) {
            payload[offset + i] = (byte) (payload[offset + i] ^ mask[i % 4]);
        }
        return payload;
    }
}
