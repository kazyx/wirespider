/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.Base64;
import net.kazyx.wirespider.util.WsLog;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SecureSessionTest {
    private static final String TAG = SecureSessionTest.class.getSimpleName();

    public static class ExternalServerEchoTest {
        @BeforeClass
        public static void setupClass() throws Exception {
            Base64.setEncoder(new Base64Encoder());
            WsLog.logLevel(WsLog.Level.DEBUG);
        }

        private void echoExternalServer(String url, final String echoMessage) throws ExecutionException, InterruptedException, TimeoutException, IOException, NoSuchAlgorithmException {
            final CustomLatch latch = new CustomLatch(1);
            WebSocketHandler handler = new WebSocketHandler() {
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
            };
            SessionRequest req = new SessionRequest.Builder(URI.create(url), handler)
                    .setConnectionTimeout(5, TimeUnit.SECONDS)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();

            try (WebSocket ws = factory.openAsync(req, true).get(20, TimeUnit.SECONDS)) {
                assertThat(ws.isConnected(), is(true));
                WsLog.d(TAG, "Send: " + echoMessage);
                ws.sendTextMessageAsync(echoMessage);
                assertThat(latch.awaitSuccess(5, TimeUnit.SECONDS), is(true));
            } finally {
                factory.destroy();
            }
        }

        @Test
        public void echoWebSocketOrgInsecure() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException {
            echoExternalServer("ws://echo.websocket.org", TestUtil.fixedLengthRandomString(128));
        }

        @Test
        public void echoWebSocketOrgSecureDefaultPort() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException {
            // Note: this test fails on JDK 7, since it disables TLS 1.1 on the client side by default.
            echoExternalServer("wss://echo.websocket.org", TestUtil.fixedLengthRandomString(128));
        }

        @Test
        public void echoWebSocketOrgSecurePort443() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException {
            // Note: this test fails on JDK 7, since it disables TLS 1.1 on the client side by default.
            echoExternalServer("wss://echo.websocket.org:443", TestUtil.fixedLengthRandomString(128));
        }

        /* Sometimes fails. Need to be fixed.
        @Test
        public void echoWebSocketOrgSecureLargeMessage() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException {
            echoExternalServer("wss://echo.websocket.org", TestUtil.fixedLengthRandomString(4096));
        }
        */
    }

    public static class SSLContextTest {
        @BeforeClass
        public static void setupClass() throws Exception {
            Base64.setEncoder(new Base64Encoder());
            WsLog.logLevel(WsLog.Level.DEBUG);
        }

        @After
        public void tearDown() throws NoSuchAlgorithmException {
            WebSocketFactory.setSslContext(null);
        }

        private void echoExternalServer(String protocol) throws ExecutionException, InterruptedException, TimeoutException, IOException, NoSuchAlgorithmException, KeyManagementException {
            final CustomLatch latch = new CustomLatch(1);
            WebSocketHandler handler = new WebSocketHandler() {
                @Override
                public void onTextMessage(String message) {
                    WsLog.d(TAG, "Received: " + message);
                    if (message.equals("hello")) {
                        latch.countDown();
                    } else {
                        WsLog.d(TAG, "Message not matched: " + message);
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
            };
            SessionRequest req = new SessionRequest.Builder(URI.create("wss://echo.websocket.org"), handler)
                    .setConnectionTimeout(5, TimeUnit.SECONDS)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();
            SSLContext context = SSLContext.getInstance(protocol);
            context.init(null, null, null);
            WebSocketFactory.setSslContext(context);

            try (WebSocket ws = factory.openAsync(req, true).get(20, TimeUnit.SECONDS)) {
                assertThat(ws.isConnected(), is(true));
                WsLog.d(TAG, "Send: hello");
                ws.sendTextMessageAsync("hello");
                assertThat(latch.awaitSuccess(5, TimeUnit.SECONDS), is(true));
            } finally {
                factory.destroy();
            }
        }

        @Test
        public void TLSv1_1() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException, KeyManagementException {
            echoExternalServer("TLSv1.1");
        }

        @Test
        public void TLSv1_2() throws InterruptedException, ExecutionException, TimeoutException, NoSuchAlgorithmException, IOException, KeyManagementException {
            echoExternalServer("TLSv1.2");
        }
    }
}
