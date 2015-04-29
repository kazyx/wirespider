package net.kazyx.apti;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

final class ByteArrayUtil {
    private ByteArrayUtil() {
    }

    static String toText(byte[] bytes) {
        try {
            return new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    static byte[] fromText(String text) {
        try {
            return text.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    static long toLong(byte[] bytes) {
        long value = 0;
        for (byte b : bytes) {
            value = (value << 8) + (b & 0xFF);
        }
        return value;
    }

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
