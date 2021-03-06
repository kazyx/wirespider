/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.util;

/**
 * Base64 encoder injection.
 */
public class Base64 {
    private Base64() {
    }

    private static Encoder sEncoder;

    /**
     * Set Base64 encoder instance.
     *
     * @param encoder Encoder.
     */
    public static void setEncoder(Encoder encoder) {
        ArgumentCheck.rejectNull(encoder);
        sEncoder = encoder;
    }

    static Encoder encoder() {
        if (sEncoder == null) {
            throw new IllegalStateException("Base64.Encoder is not set yet. See Base64#setEncoder(Base64.Encoder)");
        }
        return sEncoder;
    }

    public interface Encoder {
        /**
         * Encode byte array to Base64 string.
         *
         * @param source byte array to be encoded.
         * @return Base64 string.
         */
        String encode(byte[] source);
    }
}
