package net.kazyx.apti;

import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RxErrorEndTest {

    private void serverViolationTest(byte[] data) {
        final CustomLatch latch = new CustomLatch(1);
        Rfc6455Rx rx = new Rfc6455Rx(new FailOnCallbackRxListener() {
            @Override
            public void onProtocolViolation() {
                latch.countDown();
            }
        }, 1000, true);
        rx.onDataReceived(TestUtil.asLinkedList(data));
        assertThat(latch.isUnlockedByCountDown(), is(true));
    }

    private void clientViolationTest(byte[] data) {
        final CustomLatch latch = new CustomLatch(1);
        Rfc6455Rx rx = new Rfc6455Rx(new FailOnCallbackRxListener() {
            @Override
            public void onProtocolViolation() {
                latch.countDown();
            }
        }, 1000, false);
        rx.onDataReceived(TestUtil.asLinkedList(data));
        assertThat(latch.isUnlockedByCountDown(), is(true));
    }

    @Test
    public void rsv1NotZero() {
        byte[] data = {(byte) 0b11000001};
        serverViolationTest(data);
    }

    @Test
    public void rsv2NotZero() {
        byte[] data = {(byte) 0b10100001};
        serverViolationTest(data);
    }

    @Test
    public void rsv3NotZero() {
        byte[] data = {(byte) 0b10010001};
        serverViolationTest(data);
    }

    @Test
    public void dataFromClientNotMasked() {
        byte[] data = {(byte) 0b10000001, (byte) 0b00000001};
        clientViolationTest(data);
    }

    @Test
    public void dataFromServerMasked() {
        byte[] data = {(byte) 0b10000001, (byte) 0b10000001};
        serverViolationTest(data);
    }

    private void undefinedOpcodeTest(byte opcode) {
        byte[] data = {(byte) ((byte) 0b10000000 + opcode), 0};
        serverViolationTest(data);
    }

    @Test
    public void undefinedOpcode0x03() {
        undefinedOpcodeTest((byte) 0x03);
    }

    @Test
    public void undefinedOpcode0x04() {
        undefinedOpcodeTest((byte) 0x04);
    }

    @Test
    public void undefinedOpcode0x05() {
        undefinedOpcodeTest((byte) 0x05);
    }

    @Test
    public void undefinedOpcode0x06() {
        undefinedOpcodeTest((byte) 0x06);
    }

    @Test
    public void undefinedOpcode0x07() {
        undefinedOpcodeTest((byte) 0x07);
    }

    @Test
    public void undefinedOpcode0x0B() {
        undefinedOpcodeTest((byte) 0x0b);
    }

    @Test
    public void undefinedOpcode0x0C() {
        undefinedOpcodeTest((byte) 0x0c);
    }

    @Test
    public void undefinedOpcode0x0D() {
        undefinedOpcodeTest((byte) 0x0d);
    }

    @Test
    public void undefinedOpcode0x0E() {
        undefinedOpcodeTest((byte) 0x0e);
    }

    @Test
    public void undefinedOpcode0x0F() {
        undefinedOpcodeTest((byte) 0x0f);
    }

    @Test
    public void suddenContinuationFrame() {
        byte[] data = {(byte) 0b10000000, (byte) 0b00000001, (byte) 0x01};
        serverViolationTest(data);
    }

    @Test
    public void nonFinalForCloseFrame() {
        byte[] data = {(byte) 0x08, (byte) 0x00};
        serverViolationTest(data);
    }

    @Test
    public void nonFinalForPingFrame() {
        byte[] data = {(byte) 0x09, (byte) 0x00};
        serverViolationTest(data);
    }

    @Test
    public void nonFinalForPongFrame() {
        byte[] data = {(byte) 0x0a, (byte) 0x00};
        serverViolationTest(data);
    }

    private void payloadOverflowTest(byte[] data, byte[] overflowData, final int limit) {
        final CustomLatch latch = new CustomLatch(1);
        Rfc6455Rx rx = new Rfc6455Rx(new FailOnCallbackRxListener() {
            @Override
            public void onBinaryMessage(byte[] message) {
                assertThat(message.length, is(limit));
                assertThat(latch.getCount(), is(1L));
            }

            @Override
            public void onPayloadOverflow() {
                latch.countDown();
            }
        }, limit, true);
        rx.onDataReceived(TestUtil.asLinkedList(data));
        assertThat(latch.getCount(), is(1L));
        rx.onDataReceived(TestUtil.asLinkedList(overflowData));
        assertThat(latch.isUnlockedByCountDown(), is(true));
    }

    @Test
    public void payloadOverflowAtNonExtended() {
        int limit = 124;
        byte[] payload = TestUtil.fixedLengthByteArray(limit + 1);

        byte[] data = new byte[2 + limit];
        data[0] = (byte) 0b10000010;
        data[1] = (byte) limit;
        System.arraycopy(payload, 0, data, 2, limit);

        byte[] overflowData = new byte[2 + limit + 1];
        overflowData[0] = (byte) 0b10000010;
        overflowData[1] = (byte) (limit + 1);
        System.arraycopy(payload, 0, overflowData, 2, limit + 1);
        payloadOverflowTest(data, overflowData, limit);
    }

    @Test
    public void payloadOverflowAtNonExtendedEnd() {
        int limit = 125;
        byte[] payload = TestUtil.fixedLengthByteArray(limit + 1);

        byte[] data = new byte[2 + limit];
        data[0] = (byte) 0b10000010;
        data[1] = (byte) limit;
        System.arraycopy(payload, 0, data, 2, limit);

        byte[] overflowData = new byte[4 + limit + 1];
        overflowData[0] = (byte) 0b10000010;
        overflowData[1] = 126;
        overflowData[2] = 0;
        overflowData[3] = (byte) (limit + 1);
        System.arraycopy(payload, 0, overflowData, 4, limit + 1);

        payloadOverflowTest(data, overflowData, limit);
    }

    @Test
    public void payloadOverflowAtFirstExtensionBeginning() {
        int limit = 126;
        byte[] payload = TestUtil.fixedLengthByteArray(limit + 1);

        byte[] data = new byte[4 + limit];
        data[0] = (byte) 0b10000010;
        data[1] = 126;
        data[2] = 0;
        data[3] = (byte) limit;
        System.arraycopy(payload, 0, data, 4, limit);

        byte[] overflowData = new byte[4 + limit + 1];
        overflowData[0] = (byte) 0b10000010;
        overflowData[1] = 126;
        overflowData[2] = 0;
        overflowData[3] = (byte) (limit + 1);
        System.arraycopy(payload, 0, overflowData, 4, limit + 1);

        payloadOverflowTest(data, overflowData, limit);
    }

    @Test
    public void payloadOverflowAtFirstExtensionEnd() {
        int limit = 0x00ffff;
        byte[] payload = TestUtil.fixedLengthByteArray(limit + 1);

        byte[] data = new byte[4 + limit];
        data[0] = (byte) 0b10000010;
        data[1] = 126;
        data[2] = (byte) 0xff;
        data[3] = (byte) 0xff;
        System.arraycopy(payload, 0, data, 4, limit);

        byte[] overflowData = new byte[10 + limit + 1];
        overflowData[0] = (byte) 0b10000010;
        overflowData[1] = 127;
        overflowData[2] = 0;
        overflowData[3] = 0;
        overflowData[4] = 0;
        overflowData[5] = 0;
        overflowData[6] = 0;
        overflowData[7] = 1;
        overflowData[8] = 0;
        overflowData[9] = 0;
        System.arraycopy(payload, 0, overflowData, 10, limit + 1);

        payloadOverflowTest(data, overflowData, limit);
    }

    @Test
    public void payloadOverflowAtSecondExtensionBeginning() {
        int limit = 0x010000;
        byte[] payload = TestUtil.fixedLengthByteArray(limit + 1);

        byte[] data = new byte[10 + limit];
        data[0] = (byte) 0b10000010;
        data[1] = 127;
        data[2] = 0;
        data[3] = 0;
        data[4] = 0;
        data[5] = 0;
        data[6] = 0;
        data[7] = 1;
        data[8] = 0;
        data[9] = 0;
        System.arraycopy(payload, 0, data, 10, limit);

        byte[] overflowData = new byte[10 + limit + 1];
        overflowData[0] = (byte) 0b10000010;
        overflowData[1] = 127;
        overflowData[2] = 0;
        overflowData[3] = 0;
        overflowData[4] = 0;
        overflowData[5] = 0;
        overflowData[6] = 0;
        overflowData[7] = 1;
        overflowData[8] = 0;
        overflowData[9] = 1;
        System.arraycopy(payload, 0, overflowData, 10, limit + 1);

        payloadOverflowTest(data, overflowData, limit);
    }

    @Test
    public void payloadOverflowMaxInt() {
        int payloadSize = 10;
        byte[] payload = TestUtil.fixedLengthByteArray(payloadSize); // dummy payload. process will end before this data is read.

        byte[] overflowData = new byte[10 + payloadSize];
        overflowData[0] = (byte) 0b10000010;
        overflowData[1] = 127;
        overflowData[2] = 0;
        overflowData[3] = 0;
        overflowData[4] = 0;
        overflowData[5] = 1;
        overflowData[6] = 0;
        overflowData[7] = 0;
        overflowData[8] = 0;
        overflowData[9] = 0;
        System.arraycopy(payload, 0, overflowData, 10, payloadSize);

        final CustomLatch latch = new CustomLatch(1);
        Rfc6455Rx rx = new Rfc6455Rx(new FailOnCallbackRxListener() {
            @Override
            public void onPayloadOverflow() {
                // Just checks long int range in extended payload length is treated as payload overflow.
                latch.countDown();
            }
        }, Integer.MAX_VALUE, true);
        rx.onDataReceived(TestUtil.asLinkedList(overflowData));
        assertThat(latch.isUnlockedByCountDown(), is(true));
    }

    @Test
    public void pingPayloadMaximum() throws UnsupportedEncodingException {
        int payloadSize = 125;
        byte[] payload = TestUtil.fixedLengthString(payloadSize).getBytes("UTF-8");
        byte[] data = new byte[2 + payloadSize];
        data[0] = (byte) 0b10001001;
        data[1] = (byte) payloadSize;
        System.arraycopy(payload, 0, data, 2, payloadSize);

        final CustomLatch latch = new CustomLatch(1);
        Rfc6455Rx rx = new Rfc6455Rx(new FailOnCallbackRxListener() {
            @Override
            public void onPingFrame(String message) {
                latch.countDown();
            }
        }, 1000, true);
        rx.onDataReceived(TestUtil.asLinkedList(data));
        assertThat(latch.isUnlockedByCountDown(), is(true));
    }

    @Test
    public void pingPayloadTooLarge() throws UnsupportedEncodingException {
        int payloadSize = 126;
        byte[] payload = TestUtil.fixedLengthString(payloadSize).getBytes("UTF-8");
        byte[] data = new byte[4 + payloadSize];
        data[0] = (byte) 0b10001001;
        data[1] = 126;
        data[2] = 0;
        data[3] = (byte) payloadSize;
        System.arraycopy(payload, 0, data, 4, payloadSize);

        serverViolationTest(data);
    }
}
