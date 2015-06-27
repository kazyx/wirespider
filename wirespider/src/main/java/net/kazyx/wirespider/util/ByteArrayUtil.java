package net.kazyx.wirespider.util;

import java.io.UnsupportedEncodingException;

public final class ByteArrayUtil {
    private ByteArrayUtil() {
    }

    /**
     * Convert byte array to UTF-8 String.
     *
     * @param bytes Source byte array.
     * @return String expression of the byte array.
     */
    public static String toText(byte[] bytes) {
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Convert byte array to UTF-8 String.
     *
     * @param bytes Source byte array.
     * @param offset The index of the first byte to decode
     * @param length The number of bytes to decode
     * @return String expression of the byte array.
     */
    public static String toText(byte[] bytes, int offset, int length) {
        try {
            return new String(bytes, offset, length, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Convert UTF-8 String to byte array expression.
     *
     * @param text Source String.
     * @return Byte array expression of the String.
     */
    public static byte[] fromText(String text) {
        try {
            return text.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Convert byte array to long.
     *
     * @param bytes Source byte array.
     * @return The long value
     * @throws IllegalArgumentException Value exceeds int 64.
     */
    public static long toUnsignedLong(byte[] bytes) {
        if (8 < bytes.length) {
            throw new IllegalArgumentException("bit length overflow: " + bytes.length);
        }
        long value = 0;
        for (byte b : bytes) {
            value = (value << 8) + (b & 0xFF);
        }
        if (value < 0) {
            throw new IllegalArgumentException("Exceeds int64 range: " + value);
        }
        return value;
    }

    /**
     * Convert byte array to unsigned integer.
     *
     * @param bytes Source byte array.
     * @return The unsigned integer value.
     * @throws IllegalArgumentException Value exceeds int 32.
     */
    public static int toUnsignedInteger(byte[] bytes) {
        long l = toUnsignedLong(bytes);
        if (Integer.MAX_VALUE < l) {
            // TODO support large payload over 2GB
            throw new IllegalArgumentException("Exceeds int32 range: " + l);
        }
        return (int) l;
    }
}
