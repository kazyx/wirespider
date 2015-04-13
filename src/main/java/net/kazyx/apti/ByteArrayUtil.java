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

    static long toLong(byte[] b) {
        long value = 0;
        for (int i = 0; i < b.length; i++) {
            int shift = (b.length - 1 - i) << 3;
            value += (b[i] & BitMask.BYTE_SYM_0xFF) << shift;
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
