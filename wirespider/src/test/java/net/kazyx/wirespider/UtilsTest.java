package net.kazyx.wirespider;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(Enclosed.class)
public class UtilsTest {
    public static class TextUtilTest {
        @Test
        public void nullString() {
            assertThat(TextUtil.isNullOrEmpty(null), is(true));
        }

        @Test
        public void emptyString() {
            assertThat(TextUtil.isNullOrEmpty(""), is(true));
        }

        @Test
        public void nonNullOrEmptyString() {
            assertThat(TextUtil.isNullOrEmpty("a"), is(false));
        }
    }

    public static class IOUtilTest {
        @Test
        public void closeNull() {
            IOUtil.close(null);
        }
    }

    public static class ByteArrayUtilTest {
        @Test(expected = IllegalArgumentException.class)
        public void bit72ToLong() throws PayloadSizeOverflowException {
            byte[] _9BytesZero = new byte[9];
            ByteArrayUtil.toUnsignedLong(_9BytesZero);
        }

        @Test(expected = PayloadSizeOverflowException.class)
        public void bit64ToLongSignedBitOn() throws PayloadSizeOverflowException {
            byte[] _8BytesZero = new byte[8];
            _8BytesZero[0] = (byte) 0b10000000;
            ByteArrayUtil.toUnsignedLong(_8BytesZero);
        }

        @Test
        public void bit64ToLong() throws PayloadSizeOverflowException {
            byte[] _8BytesZero = new byte[8];
            assertThat(ByteArrayUtil.toUnsignedLong(_8BytesZero), is(0L));
        }

        @Test
        public void bit32ToInteger() throws PayloadSizeOverflowException {
            byte[] _4BytesZero = {(byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff};
            assertThat(ByteArrayUtil.toUnsignedInteger(_4BytesZero), is(Integer.MAX_VALUE));
        }
    }
}
