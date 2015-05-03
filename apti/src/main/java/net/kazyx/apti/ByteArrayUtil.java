package net.kazyx.apti;

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
     * @throws ProtocolViolationException Length of the byte array is larger than 8.
     */
    static long toLong(byte[] bytes) throws ProtocolViolationException {
        if (bytes.length > 8) {
            throw new ProtocolViolationException("bit length overflow: " + bytes.length);
        }
        long value = 0;
        for (byte b : bytes) {
            value = (value << 8) + (b & 0xFF);
        }
        return value;
    }

    /**
     * Convert byte array to unsigned integer.
     *
     * @param bytes Source byte array.
     * @return The unsigned integer value.
     * @throws ProtocolViolationException Source byte array is out of unsigned integer range.
     */
    static int toUnsignedInteger(byte[] bytes) throws ProtocolViolationException {
        long l = toLong(bytes);
        if (l < 0 || l > Integer.MAX_VALUE) {
            // TODO support large payload over 2GB
            throw new ProtocolViolationException("Bad unsigned integer: " + l);
        }
        return (int) l;
    }

    static byte[] toSubArray(byte[] array, int start) {
        return Arrays.copyOfRange(array, start, array.length);
    }
}
