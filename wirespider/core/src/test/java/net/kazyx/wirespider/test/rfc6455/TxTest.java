/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.test.rfc6455;

import net.kazyx.wirespider.CloseStatusCode;
import net.kazyx.wirespider.FrameRx;
import net.kazyx.wirespider.FrameTx;
import net.kazyx.wirespider.SocketChannelWriter;
import net.kazyx.wirespider.test.FailOnCallbackRxListener;
import net.kazyx.wirespider.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static net.kazyx.wirespider.test.rfc6455.PackageBreaker.newRfc6455Rx;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
            public void writeAsync(ByteBuffer data) {
                writeAsync(data, false);
            }

            @Override
            public void writeAsync(ByteBuffer data, boolean calledOnSelectorThread) {
                mRx.onDataReceived(data);
            }
        }

        FrameTx mTx;
        FrameRx mRx;

        @Before
        public void setup() {
            mTx = PackageBreaker.newRfc6455Tx(new DummyWriter(), !fromServer());
        }

        @Test
        public void ping() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
            final String msg = "ping";
            mRx = newRfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onPingFrame(String message) {
                    assertThat(message, is(msg));
                }
            }, 100000, fromServer());
            mTx.sendPingAsync(msg);
        }

        @Test
        public void pong() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
            final String pong = "pong_message";
            mRx = newRfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onPongFrame(String message) {
                    assertThat(message, is(pong));
                }
            }, 100000, fromServer());
            mTx.sendPongAsync(pong);
        }

        @Test
        public void close() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
            final CloseStatusCode code = CloseStatusCode.RESERVED;
            mRx = newRfc6455Rx(new FailOnCallbackRxListener() {
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
            final String msg = TestUtil.fixedLengthFixedString(length);
            mRx = newRfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onTextMessage(String text) {
                    assertThat(text, is(msg));
                }
            }, 100000, fromServer());
            mTx.sendTextAsync(msg);
        }

        private void binary(int length) {
            final byte[] msg = TestUtil.fixedLengthRandomByteArray(length);
            mRx = newRfc6455Rx(new FailOnCallbackRxListener() {
                @Override
                public void onBinaryMessage(ByteBuffer data) {
                    assertThat(Arrays.equals(msg, data.array()), is(true));
                }
            }, 100000, fromServer());
            mTx.sendBinaryAsync(Arrays.copyOf(msg, msg.length));
        }
    }
}
