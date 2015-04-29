package net.kazyx.apti;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
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
    }

    private CountDownLatch mStartLatch;
    private CountDownLatch mEndLatch;

    @Before
    public void setup() throws Exception {
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

    @After
    public void teardown() throws Exception {
        server.stop();
        System.out.println("Server stopped");
        mEndLatch.await();
    }

    @Test
    public void connectJetty9() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(URI.create("ws://127.0.0.1:10000"), new WebSocketConnection() {
                @Override
                public void onTextMessage(String message) {

                }

                @Override
                public void onBinaryMessage(byte[] message) {

                }

                @Override
                public void onClosed(int code, String reason) {

                }
            });
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            Assert.assertTrue(ws.isConnected());
        } finally {
            if (ws != null) {
                System.out.println("Test finished. Now close WebSocket");
                ws.closeAsync();
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
            ws = factory.openAsync(URI.create("ws://localhost:10000"), new WebSocketConnection() {
                @Override
                public void onTextMessage(String message) {

                }

                @Override
                public void onBinaryMessage(byte[] message) {

                }

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
            boolean completed = latch.await(1000, TimeUnit.MILLISECONDS);
            Assert.assertTrue(completed);
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void parallelConnections() throws IOException, InterruptedException {
        final WebSocketClientFactory factory = new WebSocketClientFactory();
        ExecutorService es = Executors.newCachedThreadPool();

        int NUM_CONNECTIONS = 1000;

        final CountDownLatch latch = new CountDownLatch(NUM_CONNECTIONS);
        try {
            for (int i = 0; i < NUM_CONNECTIONS; i++) {
                es.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            factory.openAsync(URI.create("ws://localhost:10000"), new WebSocketConnection() {
                                @Override
                                public void onTextMessage(String message) {

                                }

                                @Override
                                public void onBinaryMessage(byte[] message) {

                                }

                                @Override
                                public void onClosed(int code, String reason) {

                                }
                            }).get(1000, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            throw new RuntimeException(e);
                        }
                        latch.countDown();
                    }
                });
            }

            Assert.assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } finally {
            es.shutdownNow();
            factory.destroy();
        }
    }

    private static final String MESSAGE = "echo: qwertyuiop@[asdfghjkkl:;zxcvbnm,,.";

    @Test
    public void textMessages() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        ExecutorService es = Executors.newFixedThreadPool(4);
        WebSocketClientFactory factory = new WebSocketClientFactory();

        final int NUM_MESSAGES = 100000;
        final CountDownLatch latch = new CountDownLatch(NUM_MESSAGES);

        try {
            WebSocket ws = factory.openAsync(URI.create("ws://localhost:10000"), new WebSocketConnection() {
                @Override
                public void onTextMessage(String message) {
                    if (message.equals(MESSAGE)) {
                        latch.countDown();
                    }
                }

                @Override
                public void onBinaryMessage(byte[] message) {

                }

                @Override
                public void onClosed(int code, String reason) {

                }
            }).get(1000, TimeUnit.MILLISECONDS);

            for (int i = 0; i < NUM_MESSAGES; i++) {
                ws.sendTextMessageAsync(MESSAGE);
            }

            Assert.assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } finally {
            factory.destroy();
            es.shutdownNow();
        }
    }

    public static final String CLOSE_REQUEST_MESSAGE = "close";

    @Test
    public void handleCloseOpcode() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            WebSocket ws = factory.openAsync(URI.create("ws://localhost:10000"), new WebSocketConnection() {
                @Override
                public void onTextMessage(String message) {

                }

                @Override
                public void onBinaryMessage(byte[] message) {

                }

                @Override
                public void onClosed(int code, String reason) {
                    System.out.println("handleCloseOpcode: onClosed");
                    latch.countDown();
                }
            }).get(1000, TimeUnit.MILLISECONDS);
            ws.sendTextMessageAsync(CLOSE_REQUEST_MESSAGE);

            Assert.assertTrue(latch.await(100000, TimeUnit.MILLISECONDS));
        } finally {
            factory.destroy();
        }
    }
}
