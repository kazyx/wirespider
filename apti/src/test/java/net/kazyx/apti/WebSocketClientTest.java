package net.kazyx.apti;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

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
        sHeaders = null;
        sCookies = null;
    }

    @After
    public void teardown() throws Exception {
        sHeaders = null;
        sCookies = null;
    }

    @Test
    public void connectToInvalidPort() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(URI.create("ws://127.0.0.1:10001"), new EmptyWebSocketConnection());
            ws = future.get(10000, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(IOException.class)));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void connectJetty9() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(URI.create("ws://127.0.0.1:10000"), new EmptyWebSocketConnection());
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            assertThat(ws.isConnected(), is(true));
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
                    if (code == CloseStatusCode.NORMAL_CLOSURE.asNumber()) {
                        latch.countDown();
                    } else {
                        throw new IllegalStateException("Invalid close status code");
                    }
                }
            }).get(1000, TimeUnit.MILLISECONDS);

            factory.destroy();
            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
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

            assertThat(latch.await(5000, TimeUnit.MILLISECONDS), is(true));
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
    public void x100000TextMessagesEcho() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();

        final int NUM_MESSAGES = 100000;
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

            assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
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
                    if (code == CloseStatusCode.NORMAL_CLOSURE.asNumber()) {
                        latch.countDown();
                    }
                }
            }).get(1000, TimeUnit.MILLISECONDS);
            ws.sendTextMessageAsync(JettyWebSocketServlet.CLOSE_REQUEST);

            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void echoText_0000001Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoText(1);
    }

    @Test
    public void echoText_0000126Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoText(126);
    }

    @Test
    public void echoText_0000127Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoText(127);
    }

    @Test
    public void echoText_0000128Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoText(128);
    }

    @Test
    public void echoText_0010000Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoText(10000);
    }

    @Test
    public void echoText_0065536Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoText(65536);
    }

    @Test
    public void echoText_1000000Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoText(JettyWebSocketServlet.MAX_SIZE_1MB);
    }

    @Test
    public void echoBinary_0000001Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoBinary(1);
    }

    @Test
    public void echoBinary_0000126Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoBinary(126);
    }

    @Test
    public void echoBinary_0000127Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoBinary(127);
    }

    @Test
    public void echoBinary_0000128Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoBinary(128);
    }

    @Test
    public void echoBinary_0010000Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoBinary(10000);
    }

    @Test
    public void echoBinary_0065536Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoBinary(65536);
    }

    @Test
    public void echoBinary_1000000Byte() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientTestUtil.echoBinary(JettyWebSocketServlet.MAX_SIZE_1MB);
    }

    static void callbackCookies(List<HttpCookie> cookies) {
        sCookies = cookies;
    }

    private static List<HttpCookie> sCookies;

    static void callbackHeaders(Map<String, List<String>> headers) {
        sHeaders = headers;
    }

    private static Map<String, List<String>> sHeaders;

    @Test
    public void connectWithHeaders() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        HttpHeader single = new HttpHeader.Builder("single").appendValue("value").build();
        HttpHeader multi = new HttpHeader.Builder("multi").appendValue("value1").appendValue("value2").build();
        HttpHeader multi2 = new HttpHeader.Builder("multi").appendValue("value3").build();
        HttpHeader[] headers = {single, multi, multi2};
        try {
            Future<WebSocket> future = factory.openAsync(URI.create("ws://127.0.0.1:10000"), new EmptyWebSocketConnection(), Arrays.asList(headers));
            ws = future.get(1000, TimeUnit.MILLISECONDS);

            assertThat(sHeaders.keySet(), hasItems("single", "multi"));
            assertThat(sHeaders.get("single"), is(contains("value")));
            assertThat(sHeaders.get("multi"), is(contains("value1,value2", "value3")));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void connectWithCookies() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        HttpHeader cookie = new HttpHeader.Builder("Cookie").appendValue("name1=value1").appendValue("name2=value2").build();
        HttpHeader[] headers = {cookie};
        try {
            Future<WebSocket> future = factory.openAsync(URI.create("ws://127.0.0.1:10000"), new EmptyWebSocketConnection(), Arrays.asList(headers));
            ws = future.get(1000, TimeUnit.MILLISECONDS);

            assertThat(sCookies, is(notNullValue()));
            assertThat(sCookies, hasSize(2));
            for (HttpCookie c : sCookies) {
                switch (c.getName()) {
                    case "name1":
                        assertThat(c.getValue(), is("value1"));
                        break;
                    case "name2":
                        assertThat(c.getValue(), is("value2"));
                        break;
                    default:
                        fail();
                        break;
                }
            }
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }
}
