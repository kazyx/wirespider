package net.kazyx.apti;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WebSocketClientTest {
    private static Server server = new Server(10000);

    @BeforeClass
    public static void setupClass() throws Exception {
        WebSocketServlet servlet = new WebSocketServlet() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.register(JettyWebSocketServlet.class);
            }
        };

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.addServlet(new ServletHolder(servlet), "/");
        server.setHandler(handler);

        Base64.setEncoder(new Base64.Encoder() {
            @Override
            public String encode(byte[] source) {
                return org.apache.commons.codec.binary.Base64.encodeBase64String(source);
            }
        });

        mStartLatch = new CountDownLatch(1);
        mEndLatch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.start();
                    System.out.println("Server started");
                    mStartLatch.countDown();
                    server.join();
                    System.out.println("Server finished");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mEndLatch.countDown();
                }
            }
        }).start();

        mStartLatch.await();
    }

    private static CountDownLatch mStartLatch;
    private static CountDownLatch mEndLatch;

    @AfterClass
    public static void teardownClass() throws Exception {
        server.stop();
        System.out.println("Server stopped");
        mEndLatch.await();
    }

    @Before
    public void setup() throws Exception {
    }

    @After
    public void teardown() throws Exception {
    }

    @Test
    public void connectJetty9() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(URI.create("ws://127.0.0.1:10000"), new EmptyWebSocketConnection());
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            Assert.assertTrue(ws.isConnected());
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void onCloseIsCalledIfFactoryIsDestroyed() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection() {
                @Override
                public void onClosed(int code, String reason) {
                    System.out.println("WebSocketConnection onClosed");
                    if (code == CloseStatusCode.NORMAL_CLOSURE.getStatusCode()) {
                        latch.countDown();
                    } else {
                        throw new IllegalStateException("Invalid close status code");
                    }
                }
            }).get(1000, TimeUnit.MILLISECONDS);

            factory.destroy();
            boolean completed = latch.await(500, TimeUnit.MILLISECONDS);
            Assert.assertTrue(completed);
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void x100ParallelConnections() throws IOException, InterruptedException {
        final WebSocketClientFactory factory = new WebSocketClientFactory();
        ExecutorService es = Executors.newCachedThreadPool();

        final Set<WebSocket> set = new HashSet<>();
        int NUM_CONNECTIONS = 100;

        final CountDownLatch latch = new CountDownLatch(NUM_CONNECTIONS);
        try {
            for (int i = 0; i < NUM_CONNECTIONS; i++) {
                es.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Future<WebSocket> future = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection());
                            WebSocket ws = future.get(1000, TimeUnit.MILLISECONDS);
                            set.add(ws);
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            throw new RuntimeException(e);
                        }
                        latch.countDown();
                    }
                });
            }

            Assert.assertTrue(latch.await(20000, TimeUnit.MILLISECONDS));
        } finally {
            for (WebSocket ws : set) {
                ws.closeNow();
            }
            es.shutdownNow();
            factory.destroy();
        }
    }

    private static final String MESSAGE = "echo: qwertyuiop@[asdfghjkkl:;zxcvbnm,,.";

    @Test
    public void x10000TextMessagesEcho() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();

        final int NUM_MESSAGES = 10000;
        final CountDownLatch latch = new CountDownLatch(NUM_MESSAGES);
        WebSocket ws = null;
        try {
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection() {
                @Override
                public void onTextMessage(String message) {
                    // System.out.println("onTextMessage");
                    if (message.equals(MESSAGE)) {
                        latch.countDown();
                    }
                }
            }).get(1000, TimeUnit.MILLISECONDS);

            System.out.println("Start sending messages");
            for (int i = 0; i < NUM_MESSAGES; i++) {
                ws.sendTextMessageAsync(MESSAGE);
            }

            Assert.assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void handleCloseOpcode() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        final CountDownLatch latch = new CountDownLatch(1);
        WebSocket ws = null;
        try {
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new EmptyWebSocketConnection() {
                @Override
                public void onClosed(int code, String reason) {
                    System.out.println("onClosed: " + code);
                    if (code == CloseStatusCode.NORMAL_CLOSURE.getStatusCode()) {
                        latch.countDown();
                    }
                }
            }).get(1000, TimeUnit.MILLISECONDS);
            ws.sendTextMessageAsync(JettyWebSocketServlet.CLOSE_REQUEST);

            Assert.assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void textEcho1Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        textEcho(1);
    }

    @Test
    public void textEcho126Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        textEcho(126);
    }

    @Test
    public void textEcho127Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        textEcho(127);
    }

    @Test
    public void textEcho128Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        textEcho(128);
    }

    @Test
    public void textEcho10000Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        textEcho(10000);
    }

    @Test
    public void textEcho65536Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        textEcho(65536);
    }

    @Test
    public void textEcho1MByte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        textEcho(JettyWebSocketServlet.MAX_SIZE_1MB);
    }

    @Test
    public void binaryEcho1Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        binaryEcho(1);
    }

    @Test
    public void binaryEcho126Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        binaryEcho(126);
    }

    @Test
    public void binaryEcho127Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        binaryEcho(127);
    }

    @Test
    public void binaryEcho128Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        binaryEcho(128);
    }

    @Test
    public void binaryEcho10000Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        binaryEcho(10000);
    }

    @Test
    public void binaryEcho65536Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        binaryEcho(65536);
    }

    @Test
    public void binaryEcho1MByte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        binaryEcho(JettyWebSocketServlet.MAX_SIZE_1MB);
    }

    private static void binaryEcho(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
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
            Assert.assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    private static byte[] fixedLengthByteArray(int length) {
        byte[] ba = new byte[length];
        for (int i = 0; i < length; i++) {
            ba[i] = 10;
        }
        return ba;
    }

    private static void textEcho(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
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
            Assert.assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    private static String fixedLengthString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("a");
        }
        return sb.toString();
    }
}
