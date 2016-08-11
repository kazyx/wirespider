/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.test;

import net.kazyx.wirespider.CloseStatusCode;
import net.kazyx.wirespider.SessionRequest;
import net.kazyx.wirespider.WebSocket;
import net.kazyx.wirespider.WebSocketFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class WebSocketClientTestUtil {
    private WebSocketClientTestUtil() {
    }

    public static void payloadLimit(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch messageLatch = new CustomLatch(2);
        final CustomLatch closeLatch = new CustomLatch(1);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setCloseHandler((code, reason) -> {
                    if (code == CloseStatusCode.MESSAGE_TOO_BIG.asNumber()) {
                        closeLatch.countDown();
                    } else {
                        closeLatch.unlockByFailure();
                    }
                })
                .setBinaryHandler(message -> {
                            System.out.println("onBinaryMessage" + message.length);
                            messageLatch.countDown();
                        }
                ).setMaxResponsePayloadSizeInBytes(size)
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            ws.sendBinaryMessageAsync(TestUtil.fixedLengthRandomByteArray(size - 1));
            ws.sendBinaryMessageAsync(TestUtil.fixedLengthRandomByteArray(size));
            assertThat(messageLatch.await(500, TimeUnit.MILLISECONDS), is(true));

            ws.sendBinaryMessageAsync(TestUtil.fixedLengthRandomByteArray(size + 1));
            assertThat(closeLatch.await(500, TimeUnit.MILLISECONDS), is(true));
            assertThat(closeLatch.isUnlockedByFailure(), is(false));
        } finally {
            factory.destroy();
        }
    }

    public static void echoBinary(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        byte[] data = TestUtil.fixedLengthRandomByteArray(size);
        final byte[] copy = Arrays.copyOf(data, data.length);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setCloseHandler((code, reason) -> latch.unlockByFailure())
                .setBinaryHandler(message -> {
                            if (Arrays.equals(message, copy)) {
                                latch.countDown();
                            } else {
                                System.out.println("Binary message not matched");
                                latch.unlockByFailure();
                            }
                        }
                ).setMaxResponsePayloadSizeInBytes(size)
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            ws.sendBinaryMessageAsync(data);
            assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByFailure(), is(false));
        } finally {
            factory.destroy();
        }
    }

    public static void echoText(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        final String data = TestUtil.fixedLengthFixedString(size);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setCloseHandler((code, reason) -> latch.unlockByFailure())
                .setTextHandler(message -> {
                            if (data.equals(message)) {
                                latch.countDown();
                            } else {
                                System.out.println("Text message not matched");
                                latch.unlockByFailure();
                            }
                        }
                ).setMaxResponsePayloadSizeInBytes(size)
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            ws.sendTextMessageAsync(data);
            assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByFailure(), is(false));
        } finally {
            factory.destroy();
        }
    }
}
