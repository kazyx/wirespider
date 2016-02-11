/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;

public class TestUtil {
    static byte[] fixedLengthRandomByteArray(int length) {
        System.out.println("Create random byte array: " + length);
        byte[] ba = new byte[length];
        Random rnd = new Random(20L);
        rnd.nextBytes(ba);
        return ba;
    }

    static byte[] fixedLengthFixedByteArray(int length) {
        System.out.println("Create random byte array: " + length);
        byte[] ba = new byte[length];
        for (int i = 0; i < length; i++) {
            ba[i] = (byte) 0x01;
        }
        return ba;
    }

    static String fixedLengthRandomString(int length) {
        return RandomStringUtils.random(length);
    }

    static String fixedLengthFixedString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("a");
        }
        return sb.toString();
    }

    static LinkedList<ByteBuffer> asLinkedList(byte[] data) {
        return new LinkedList<>(Collections.singletonList(ByteBuffer.wrap(data)));
    }

    static LinkedList<ByteBuffer> asLinkedList(String data) {
        try {
            return asLinkedList(data.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
