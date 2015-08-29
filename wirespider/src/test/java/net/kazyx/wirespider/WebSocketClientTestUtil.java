/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WebSocketClientTestUtil {
    private WebSocketClientTestUtil() {
    }

    static void payloadLimit(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch messageLatch = new CustomLatch(2);
        final CustomLatch closeLatch = new CustomLatch(1);
        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                if (code == CloseStatusCode.MESSAGE_TOO_BIG.asNumber()) {
                    closeLatch.countDown();
                } else {
                    closeLatch.unlockByFailure();
                }
            }

            @Override
            public void onBinaryMessage(byte[] message) {
                System.out.println("onBinaryMessage" + message.length);
                messageLatch.countDown();
            }
        }).setMaxResponsePayloadSizeInBytes(size).build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);

            ws.sendBinaryMessageAsync(TestUtil.fixedLengthRandomByteArray(size - 1));
            ws.sendBinaryMessageAsync(TestUtil.fixedLengthRandomByteArray(size));
            assertThat(messageLatch.await(500, TimeUnit.MILLISECONDS), is(true));

            ws.sendBinaryMessageAsync(TestUtil.fixedLengthRandomByteArray(size + 1));
            assertThat(closeLatch.await(500, TimeUnit.MILLISECONDS), is(true));
            assertThat(closeLatch.isUnlockedByFailure(), is(false));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    static void echoBinary(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        byte[] data = TestUtil.fixedLengthRandomByteArray(size);
        final byte[] copy = Arrays.copyOf(data, data.length);
        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                latch.unlockByFailure();
            }

            @Override
            public void onBinaryMessage(byte[] message) {
                if (Arrays.equals(message, copy)) {
                    latch.countDown();
                } else {
                    System.out.println("Binary message not matched");
                    latch.unlockByFailure();
                }
            }
        }).setMaxResponsePayloadSizeInBytes(size).build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);
            ws.sendBinaryMessageAsync(data);
            assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByFailure(), is(false));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    static void echoText(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        final String data = TestUtil.fixedLengthFixedString(size);
        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                latch.unlockByFailure();
            }

            @Override
            public void onTextMessage(String message) {
                if (data.equals(message)) {
                    latch.countDown();
                } else {
                    System.out.println("Text message not matched");
                    latch.unlockByFailure();
                }
            }
        }).setMaxResponsePayloadSizeInBytes(size).build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);
            ws.sendTextMessageAsync(data);
            assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByFailure(), is(false));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }
}
