/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SecureSessionTest {
    private static final String TAG = SecureSessionTest.class.getSimpleName();

    public static class ExternalServerEchoTest {
        @BeforeClass
        public static void setupClass() throws Exception {
            RandomSource.setSeed(0x12345678);
            Base64.setEncoder(new Base64Encoder());
            WsLog.logLevel(WsLog.Level.DEBUG);
        }

        private void echoExternalServer(String url, final String echoMessage) throws ExecutionException, InterruptedException, TimeoutException, IOException, NoSuchAlgorithmException {
            final CustomLatch latch = new CustomLatch(1);
            SessionRequest seed = new SessionRequest.Builder(URI.create(url), new WebSocketHandler() {
                @Override
                public void onTextMessage(String message) {
                    WsLog.d(TAG, "Received: " + message);
                    if (message.equals(echoMessage)) {
                        latch.countDown();
                    } else {
                        WsLog.d(TAG, "Message not matched: length " + echoMessage.length() + " -> " + message.length());
                        latch.unlockByFailure();
                    }
                }

                @Override
                public void onBinaryMessage(byte[] message) {
                    latch.unlockByFailure();
                }

                @Override
                public void onClosed(int code, String reason) {
                    latch.unlockByFailure();
                }
            }).build();

            WebSocketFactory factory = new WebSocketFactory();
            SecureTransport.enable(factory);
            WebSocket ws = null;
            try {
                Future<WebSocket> future = factory.openAsync(seed);
                ws = future.get(5, TimeUnit.SECONDS);
                assertThat(ws.isConnected(), is(true));
                WsLog.d(TAG, "Send: " + echoMessage);
                ws.sendTextMessageAsync(echoMessage);
                assertThat(latch.awaitSuccess(5, TimeUnit.SECONDS), is(true));
            } finally {
                if (ws != null) {
                    ws.closeNow();
                }
                factory.destroy();
            }
        }

        @Test
        public void echoWebSocketOrgInsecure() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException {
            echoExternalServer("ws://echo.websocket.org", TestUtil.fixedLengthRandomString(128));
        }

        @Test
        public void echoWebSocketOrgSecureDefaultPort() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException {
            echoExternalServer("wss://echo.websocket.org", TestUtil.fixedLengthRandomString(128));
        }

        @Test
        public void echoWebSocketOrgSecurePort443() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException {
            echoExternalServer("wss://echo.websocket.org:443", TestUtil.fixedLengthRandomString(128));
        }

        /* Sometimes fails. Need to be fixed.
        @Test
        public void echoWebSocketOrgSecureLargeMessage() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException {
            echoExternalServer("wss://echo.websocket.org", TestUtil.fixedLengthRandomString(4096));
        }
        */
    }
}
