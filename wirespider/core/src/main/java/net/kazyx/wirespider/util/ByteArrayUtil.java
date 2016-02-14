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
import java.nio.charset.Charset;

public final class ByteArrayUtil {
    private ByteArrayUtil() {
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * Convert remaining byte buffer to UTF-8 String.
     *
     * @param bytes Source byte buffer.
     * @return String expression of the bytes.
     */
    public static String toTextRemaining(ByteBuffer bytes) {
        return new String(toBytesRemaining(bytes), UTF8);
    }

    /**
     * Convert whole byte buffer to UTF-8 String.
     *
     * @param bytes Source byte buffer.
     * @return String expression of the bytes.
     */
    public static String toTextAll(ByteBuffer bytes) {
        return new String(bytes.array(), UTF8);
    }

    /**
     * Convert remaining byte buffer to byte array.
     *
     * @param buffer Source byte buffer
     * @return Remaining byte array.
     */
    public static byte[] toBytesRemaining(ByteBuffer buffer) {
        byte[] array = new byte[buffer.remaining()];
        buffer.get(array);
        return array;
    }

    /**
     * Convert UTF-8 String to byte array expression.
     *
     * @param text Source String.
     * @return Byte array expression of the String. Empty array if given text is {@code null}.
     */
    public static byte[] fromText(String text) {
        if (text == null) {
            return new byte[0];
        } else {
            return text.getBytes(UTF8);
        }
    }

    /**
     * Convert whole byte buffer to long.
     *
     * @param bytes Source byte buffer.
     * @return The long value
     * @throws IllegalArgumentException Value exceeds int 64.
     */
    public static long toUnsignedLong(ByteBuffer bytes) {
        if (8 < bytes.remaining()) {
            throw new IllegalArgumentException("bit length overflow: " + bytes.remaining());
        }
        long value = 0;
        for (byte b : bytes.array()) {
            value = (value << 8) + (b & 0xFF);
        }
        if (value < 0) {
            throw new IllegalArgumentException("Exceeds int64 range: " + value);
        }
        return value;
    }

    /**
     * Convert whole byte buffer to unsigned integer.
     *
     * @param bytes Source byte buffer.
     * @return The unsigned integer value.
     * @throws IllegalArgumentException Value exceeds int 32.
     */
    public static int toUnsignedInteger(ByteBuffer bytes) {
        long l = toUnsignedLong(bytes);
        if (Integer.MAX_VALUE < l) {
            // TODO support large payload over 2GB
            throw new IllegalArgumentException("Exceeds int32 range: " + l);
        }
        return (int) l;
    }

    private static final char[] HEX_SOURCE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Convert byte array to HEX format.
     *
     * @param bytes Source byte array
     * @return HEX format.
     */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("0x");
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(HEX_SOURCE[v >>> 4])
                    .append(HEX_SOURCE[v & 0x0F]);
        }
        return sb.toString();
    }
}
