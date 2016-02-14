/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.BinaryUtil;
import net.kazyx.wirespider.util.IOUtil;
import net.kazyx.wirespider.util.SelectionKeyUtil;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class UtilsTest {
    public static class IOUtilTest {
        @Test
        public void closeNull() {
            Closeable closeable = null;
            IOUtil.close(closeable);
        }

        @Test
        public void closeNullSelectable() {
            Selector closeable = null;
            IOUtil.close(closeable);
        }

        @Test
        public void ignoreIOException() {
            Closeable closeable = new Closeable() {
                @Override
                public void close() throws IOException {
                    throw new IOException();
                }
            };
            IOUtil.close(closeable);
        }
    }

    public static class BinaryUtilTest {
        @Test(expected = IllegalArgumentException.class)
        public void bit72ToLong() {
            byte[] _9BytesZero = new byte[9];
            BinaryUtil.toUnsignedLong(ByteBuffer.wrap(_9BytesZero));
        }

        @Test(expected = IllegalArgumentException.class)
        public void bit64ToLongSignedBitOn() {
            byte[] _8BytesZero = new byte[8];
            _8BytesZero[0] = (byte) 0b10000000;
            BinaryUtil.toUnsignedLong(ByteBuffer.wrap(_8BytesZero));
        }

        @Test
        public void bit64ToLong() {
            byte[] _8BytesZero = new byte[8];
            assertThat(BinaryUtil.toUnsignedLong(ByteBuffer.wrap(_8BytesZero)), is(0L));
        }

        @Test
        public void bit32ToInteger() {
            byte[] _4BytesZero = {(byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff};
            assertThat(BinaryUtil.toUnsignedInteger(ByteBuffer.wrap(_4BytesZero)), is(Integer.MAX_VALUE));
        }

        @Test
        public void hex() {
            byte[] original = {(byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef};
            assertThat(BinaryUtil.toHex(original), is("0x0123456789ABCDEF"));
        }

        @Test
        public void nullText() {
            byte[] empty = {};
            assertThat(Arrays.equals(BinaryUtil.fromText(null), empty), is(true));
        }
    }

    public static class SelectionKeyUtilTest {
        @Test(expected = IOException.class)
        public void wrapCancelledKeyException() throws IOException {
            SelectorProvider provider = SelectorProvider.provider();
            Selector selector = null;
            try {
                try {
                    selector = provider.openSelector();
                    SocketChannel ch = provider.openSocketChannel();
                    ch.configureBlocking(false);
                    ch.connect(new InetSocketAddress("localhost", 10000));
                    ch.register(selector, SelectionKey.OP_CONNECT);
                } catch (IOException e) {
                    fail();
                }

                for (SelectionKey key : selector.keys()) {
                    key.cancel();
                    SelectionKeyUtil.interestOps(key, SelectionKey.OP_ACCEPT);
                }
            } finally {
                if (selector != null) {
                    IOUtil.close(selector);
                }
            }
        }
    }
}
