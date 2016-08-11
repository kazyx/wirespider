/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.test;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Random;

public class TestUtil {
    public static byte[] fixedLengthRandomByteArray(int length) {
        System.out.println("Create random byte array: " + length);
        byte[] ba = new byte[length];
        Random rnd = new Random(20L);
        rnd.nextBytes(ba);
        return ba;
    }

    public static byte[] fixedLengthFixedByteArray(int length) {
        System.out.println("Create random byte array: " + length);
        byte[] ba = new byte[length];
        for (int i = 0; i < length; i++) {
            ba[i] = (byte) 0x01;
        }
        return ba;
    }

    public static String fixedLengthRandomString(int length) {
        return RandomStringUtils.random(length);
    }

    public static String fixedLengthFixedString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("a");
        }
        return sb.toString();
    }

    public static ByteBuffer asByteBuffer(String data) {
        try {
            return ByteBuffer.wrap(data.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
