/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.ByteArrayUtil;
import net.kazyx.wirespider.util.IOUtil;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UtilsTest {
    public static class IOUtilTest {
        @Test
        public void closeNull() {
            IOUtil.close(null);
        }
    }

    public static class ByteArrayUtilTest {
        @Test(expected = IllegalArgumentException.class)
        public void bit72ToLong() {
            byte[] _9BytesZero = new byte[9];
            ByteArrayUtil.toUnsignedLong(_9BytesZero);
        }

        @Test(expected = IllegalArgumentException.class)
        public void bit64ToLongSignedBitOn() {
            byte[] _8BytesZero = new byte[8];
            _8BytesZero[0] = (byte) 0b10000000;
            ByteArrayUtil.toUnsignedLong(_8BytesZero);
        }

        @Test
        public void bit64ToLong() {
            byte[] _8BytesZero = new byte[8];
            assertThat(ByteArrayUtil.toUnsignedLong(_8BytesZero), is(0L));
        }

        @Test
        public void bit32ToInteger() {
            byte[] _4BytesZero = {(byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff};
            assertThat(ByteArrayUtil.toUnsignedInteger(_4BytesZero), is(Integer.MAX_VALUE));
        }
    }
}
