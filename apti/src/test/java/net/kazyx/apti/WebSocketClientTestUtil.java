package net.kazyx.apti;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WebSocketClientTestUtil {
    private WebSocketClientTestUtil() {
    }

    static void echoBinary(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        final CountDownLatch latch = new CountDownLatch(1);
        byte[] data = fixedLengthByteArray(size);

        final byte[] copy = Arrays.copyOf(data, data.length);
        WebSocket ws = null;
        try {
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection() {
                @Override
                public void onBinaryMessage(byte[] message) {
                    if (Arrays.equals(message, copy)) {
                        latch.countDown();
                    } else {
                        System.out.println("Binary message not matched");
                    }
                }
            }).get(1000, TimeUnit.MILLISECONDS);
            ws.sendBinaryMessageAsync(data);
            assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
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
        final CountDownLatch latch = new CountDownLatch(1);
        final String data = fixedLengthString(size);

        WebSocket ws = null;
        try {
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection() {
                @Override
                public void onTextMessage(String message) {
                    if (data.equals(message)) {
                        latch.countDown();
                    } else {
                        System.out.println("Text message not matched");
                    }
                }
            }).get(1000, TimeUnit.MILLISECONDS);
            ws.sendTextMessageAsync(data);
            assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
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
