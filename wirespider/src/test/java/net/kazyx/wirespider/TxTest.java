package net.kazyx.wirespider;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(Enclosed.class)
public class TxTest {
    public static class ServerToClientTest extends TestBase {
        @Override
        public boolean fromServer() {
            return true;
        }
    }

    public static class ClientToServerTest extends TestBase {
        @Override
        public boolean fromServer() {
            return false;
        }
    }

    public abstract static class TestBase {
        public abstract boolean fromServer();

        private class DummyWriter implements SocketChannelWriter {
            @Override
            public void writeAsync(byte[] data) {
                writeAsync(data, false);
            }

            @Override
            public void writeAsync(byte[] data, boolean calledOnSelectorThread) {
                mRx.onDataReceived(TestUtil.asLinkedList(data));
            }
        }

        Rfc6455Tx mTx;
        Rfc6455Rx mRx;

        @Before
        public void setup() {
            mTx = new Rfc6455Tx(new DummyWriter(), !fromServer());
        }

        @Test
        public void ping() {
            final String msg = "ping";
            mRx = new Rfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onPingFrame(String message) {
                    assertThat(message, is(msg));
                }
            }, 100000, fromServer());
            mTx.sendPingAsync(msg);
        }

        @Test
        public void pong() {
            final String pong = "pong_message";
            mRx = new Rfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onPongFrame(String message) {
                    assertThat(message, is(pong));
                }
            }, 100000, fromServer());
            mTx.sendPongAsync(pong);
        }

        @Test
        public void close() {
            final CloseStatusCode code = CloseStatusCode.RESERVED;
            mRx = new Rfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onCloseFrame(int status, String reason) {
                    assertThat(status, is(code.asNumber()));
                    assertThat(reason, is(code.name()));
                }
            }, 100000, fromServer());
            mTx.sendCloseAsync(code, code.name());
        }

        @Test
        public void shortText() {
            text(100);
        }

        @Test
        public void middleText() {
            text(10000);
        }

        @Test
        public void largeText() {
            text(100000);
        }

        @Test
        public void shortBinary() {
            binary(100);
        }

        @Test
        public void middleBinary() {
            binary(10000);
        }

        @Test
        public void largeBinary() {
            binary(100000);
        }

        private void text(int length) {
            final String msg = TestUtil.fixedLengthString(length);
            mRx = new Rfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onTextMessage(String text) {
                    assertThat(text, is(msg));
                }
            }, 100000, fromServer());
            mTx.sendTextAsync(msg);
        }

        private void binary(int length) {
            final byte[] msg = TestUtil.fixedLengthByteArray(length);
            mRx = new Rfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onBinaryMessage(byte[] data) {
                    assertThat(Arrays.equals(msg, data), is(true));
                }
            }, 100000, fromServer());
            mTx.sendBinaryAsync(Arrays.copyOf(msg, msg.length));
        }
    }
}