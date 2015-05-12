package net.kazyx.apti;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.LinkedList;

public class TestUtil {
    static byte[] fixedLengthByteArray(int length) {
        System.out.println("Create random byte array: " + length);
        byte[] ba = new byte[length];
        for (int i = 0; i < length; i++) {
            ba[i] = 10;
        }
        return ba;
    }

    static String fixedLengthString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("a");
        }
        return sb.toString();
    }

    static LinkedList<byte[]> asLinkedList(byte[] data) {
        return new LinkedList<>(Collections.singletonList(data));
    }

    static LinkedList<byte[]> asLinkedList(String data) {
        try {
            return asLinkedList(data.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
