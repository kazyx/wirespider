package net.kazyx.wirespider;

final class BitMask {
    private BitMask() {
    }

    /**
     * Check bit flags
     *
     * @param source Source byte.
     * @param flag   Flags byte.
     * @return {@code true} if all flags are active.
     */
    static boolean isMatched(byte source, byte flag) {
        return (source & flag) == flag;
    }

    /**
     * Mask payload to send data from client.
     *
     * @param payload    Source raw payload.
     * @param maskingKey Masking key
     * @return Masked payload.
     */
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
