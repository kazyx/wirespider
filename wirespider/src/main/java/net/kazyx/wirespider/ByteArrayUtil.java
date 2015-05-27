package net.kazyx.wirespider;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

final class ByteArrayUtil {
    private ByteArrayUtil() {
    }

    /**
     * Convert byte array to UTF-8 String.
     *
     * @param bytes Source byte array.
     * @return String expression of the byte array.
     */
    static String toText(byte[] bytes) {
        try {
            return new String(bytes, "UTF-8");
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
    static byte[] fromText(String text) {
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
     * @throws IllegalArgumentException Length of the byte array is larger than 8.
     */
    static long toUnsignedLong(byte[] bytes) throws PayloadSizeOverflowException {
        if (8 < bytes.length) {
            throw new IllegalArgumentException("bit length overflow: " + bytes.length);
        }
        long value = 0;
        for (byte b : bytes) {
            value = (value << 8) + (b & 0xFF);
        }
        if (value < 0) {
            throw new PayloadSizeOverflowException("Exceeds int64 range: " + value);
        }
        return value;
    }

    /**
     * Convert byte array to unsigned integer.
     *
     * @param bytes Source byte array.
     * @return The unsigned integer value.
     * @throws IllegalArgumentException     Length of the byte array is larger than 8.
     * @throws PayloadSizeOverflowException if the size exceeds 32 bit signed integer range.
     */
    static int toUnsignedInteger(byte[] bytes) throws PayloadSizeOverflowException {
        long l = toUnsignedLong(bytes);
        if (Integer.MAX_VALUE < l) {
            // TODO support large payload over 2GB
            throw new PayloadSizeOverflowException("Exceeds int32 range: " + l);
        }
        return (int) l;
    }

    static byte[] toSubArray(byte[] array, int start) {
        return Arrays.copyOfRange(array, start, array.length);
    }
}