/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.util;

import java.nio.ByteBuffer;

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
    public static boolean isFlagMatched(byte source, byte flag) {
        return (source & flag) == flag;
    }

    /**
     * Mask payload to send data from client.
     *
     * @param payload Source raw payload.
     * @param maskingKey Masking key
     */
    public static void maskAll(ByteBuffer payload, byte[] maskingKey) {
        byte[] array = payload.array();
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) (array[i] ^ maskingKey[i & 3]); // MOD 4
        }
    }
}
