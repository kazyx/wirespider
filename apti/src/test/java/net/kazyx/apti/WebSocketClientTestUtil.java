package net.kazyx.apti;

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
        WebSocketClientFactory factory = new WebSocketClientFactory();
        factory.maxResponsePayloadSizeInBytes(size);
        WebSocket ws = null;
        final CustomLatch messageLatch = new CustomLatch(2);
        final CustomLatch closeLatch = new CustomLatch(1);
        try {
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection() {
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
            }).get(1000, TimeUnit.MILLISECONDS);

            ws.sendBinaryMessageAsync(WebSocketClientTestUtil.fixedLengthByteArray(size - 1));
            ws.sendBinaryMessageAsync(WebSocketClientTestUtil.fixedLengthByteArray(size));
            assertThat(messageLatch.await(500, TimeUnit.MILLISECONDS), is(true));

            ws.sendBinaryMessageAsync(WebSocketClientTestUtil.fixedLengthByteArray(size + 1));
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
        WebSocketClientFactory factory = new WebSocketClientFactory();
        factory.maxResponsePayloadSizeInBytes(size);
        final CustomLatch latch = new CustomLatch(1);
        byte[] data = fixedLengthByteArray(size);

        final byte[] copy = Arrays.copyOf(data, data.length);
        WebSocket ws = null;
        try {
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection() {
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
            }).get(1000, TimeUnit.MILLISECONDS);
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

    static byte[] fixedLengthByteArray(int length) {
        byte[] ba = new byte[length];
        for (int i = 0; i < length; i++) {
            ba[i] = 10;
        }
        return ba;
    }

    static void echoText(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        factory.maxResponsePayloadSizeInBytes(size);
        final CustomLatch latch = new CustomLatch(1);
        final String data = fixedLengthString(size);

        WebSocket ws = null;
        try {
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection() {
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
            }).get(1000, TimeUnit.MILLISECONDS);
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

    static String fixedLengthString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("a");
        }
        return sb.toString();
    }
}
