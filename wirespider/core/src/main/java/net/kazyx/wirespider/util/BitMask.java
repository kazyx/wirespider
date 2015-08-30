/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.util;

public final class BitMask {
    private BitMask() {
    }

    /**
     * Check bit flags
     *
     * @param source Source byte.
     * @param flag Flags byte.
     * @return {@code true} if all flags are active.
     */
    public static boolean isMatched(byte source, byte flag) {
        return (source & flag) == flag;
    }

    /**
     * Mask payload to send data from client.
     *
     * @param payload Source raw payload.
     * @param maskingKey Masking key
     * @return Masked payload.
     */
    public static byte[] maskAll(byte[] payload, byte[] maskingKey) {
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ maskingKey[i & 3]); // MOD 4
        }
        return payload;
    }
}
