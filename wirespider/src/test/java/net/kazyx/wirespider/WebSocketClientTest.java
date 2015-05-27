package net.kazyx.wirespider;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        RandomSource.seed(0x12345678);
        WebSocketServlet servlet = new WebSocketServlet() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.setCreator(new WebSocketCreator() {
                    @Override
                    public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
                        if (req.getHeader(JettyWebSocketServlet.REJECT_KEY) != null) {
                            System.out.println("JettyWebSocket: Reject upgrade");
                            return null;
                        } else {
                            return new JettyWebSocketServlet();
                        }
                    }
                });
            }
        };

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.addServlet(new ServletHolder(servlet), "/");
        server.setHandler(handler);

        Base64.encoder(new Base64.Encoder() {
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
        sPingFrameLatch = null;
        sCloseFrameLatch = null;
        sCookieCbLatch = null;
        sHeaderCbLatch = null;
        Log.logLevel(Log.Level.DEBUG);
    }

    @After
    public void teardown() throws Exception {
    }

    @Test
    public void connectJetty9() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
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
    public void nothingHappensAfterClosed() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        final CustomLatch latch = new CustomLatch(2);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new InterpretedEventHandler() {
            @Override
            public void onTextMessage(String message) {
                latch.countDown();
            }

            @Override
            public void onBinaryMessage(byte[] message) {
                latch.countDown();
            }

            @Override
            public void onPong(String message) {
                latch.countDown();
            }

            @Override
            public void onClosed(int code, String reason) {
                latch.countDown();
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            ws.closeNow();
            Thread.sleep(200);
            ws.sendTextMessageAsync("test");
            ws.sendBinaryMessageAsync("test".getBytes("UTF-8"));
            ws.sendPingAsync("ping");
            ws.closeAsync();
            Thread.sleep(500);
            assertThat(latch.getCount(), is(1L));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    static void callbackCloseFrame() {
        if (sCloseFrameLatch != null) {
            sCloseFrameLatch.countDown();
        }
    }

    private static CustomLatch sCloseFrameLatch;

    @Test
    public void gracefulClose() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        sCloseFrameLatch = new CustomLatch(1);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(500, TimeUnit.MILLISECONDS);
            ws.closeAsync();
            assertThat(sCloseFrameLatch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void shutdownSoonAfterCloseSent() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                latch.countDown();
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(500, TimeUnit.MILLISECONDS);
            ws.closeAsync(CloseStatusCode.GOING_AWAY, "Going away");
            factory.destroy();
            assertThat(latch.await(200, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByCountDown(), is(true));
            assertThat(ws.isConnected(), is(false));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void closedByAbnormalClosure() throws IOException, InterruptedException, ExecutionException, TimeoutException, NoSuchFieldException, IllegalAccessException {
        final CustomLatch latch = new CustomLatch(1);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                if (code == CloseStatusCode.ABNORMAL_CLOSURE.asNumber()) {
                    latch.countDown();
                } else {
                    latch.unlockByFailure();
                }
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(500, TimeUnit.MILLISECONDS);

            Field f = WebSocket.class.getDeclaredField("mRxListener");
            f.setAccessible(true);
            FrameRx.Listener listener = (FrameRx.Listener) f.get(ws);
            listener.onCloseFrame(CloseStatusCode.ABNORMAL_CLOSURE.asNumber(), "abnormal");

            assertThat(latch.await(200, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByCountDown(), is(true));
            assertThat(ws.isConnected(), is(false));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    static void callbackAssert() {
        if (sAssertLatch != null) {
            sAssertLatch.unlockByFailure();
        }
    }

    private static CustomLatch sAssertLatch;

    @Test
    public void requestSendAfterClose() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        sAssertLatch = new CustomLatch(1);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(500, TimeUnit.MILLISECONDS);
            ws.closeAsync();
            ws.sendTextMessageAsync(JettyWebSocketServlet.ASSERT_REQUEST);
            assertThat(sAssertLatch.await(500, TimeUnit.MILLISECONDS), is(false));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    static void callbackPingFrame() {
        if (sPingFrameLatch != null) {
            sPingFrameLatch.countDown();
        }
    }

    private static CustomLatch sPingFrameLatch;

    @Test
    public void sendPingAndReceivePong() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        sPingFrameLatch = new CustomLatch(2);
        final String msg = "ping";
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                if (sPingFrameLatch != null) {
                    sPingFrameLatch.unlockByFailure();
                }
            }

            @Override
            public void onPong(String message) {
                if (message.equals(msg)) {
                    sPingFrameLatch.countDown();
                }
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(500, TimeUnit.MILLISECONDS);

            ws.sendPingAsync(msg);
            assertThat(sPingFrameLatch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void nothingInvokedByDefaultOnPong() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        sPingFrameLatch = new CustomLatch(2);
        final String msg = "ping";
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                if (sPingFrameLatch != null) {
                    sPingFrameLatch.unlockByFailure();
                }
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(500, TimeUnit.MILLISECONDS);

            ws.sendPingAsync(msg);
            assertThat(sPingFrameLatch.await(500, TimeUnit.MILLISECONDS), is(false));
            assertThat(sPingFrameLatch.getCount(), is(1L));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    static void callbackPongFrame() {
        if (sPongFrameLatch != null) {
            sPongFrameLatch.countDown();
        }
    }

    private static CustomLatch sPongFrameLatch;

    @Test
    public void receivePing() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        sPongFrameLatch = new CustomLatch(1);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                if (sPongFrameLatch != null) {
                    sPongFrameLatch.unlockByFailure();
                }
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(500, TimeUnit.MILLISECONDS);
            ws.sendTextMessageAsync(JettyWebSocketServlet.PING_REQUEST);

            assertThat(sPongFrameLatch.await(1000, TimeUnit.MILLISECONDS), is(true));
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
        final CountDownLatch latch = new CountDownLatch(1);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                System.out.println("InterpretedEventHandler onClosed");
                if (code == CloseStatusCode.ABNORMAL_CLOSURE.asNumber()) {
                    latch.countDown();
                } else {
                    throw new IllegalStateException("Invalid close status code");
                }
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);

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

        Log.logLevel(Log.Level.ERROR);

        final CountDownLatch latch = new CountDownLatch(NUM_CONNECTIONS);
        try {
            for (int i = 0; i < NUM_CONNECTIONS; i++) {
                es.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler()).build();
                            Future<WebSocket> future = factory.openAsync(seed);
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
        final int NUM_MESSAGES = 100000;
        final CountDownLatch latch = new CountDownLatch(NUM_MESSAGES);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onTextMessage(String message) {
                // System.out.println("onTextMessage");
                if (message.equals(MESSAGE)) {
                    latch.countDown();
                }
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);

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
        final CountDownLatch latch = new CountDownLatch(1);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onClosed(int code, String reason) {
                System.out.println("onClosed: " + code);
                if (code == CloseStatusCode.NORMAL_CLOSURE.asNumber()) {
                    latch.countDown();
                }
            }
        }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);
            ws.sendTextMessageAsync(JettyWebSocketServlet.CLOSE_REQUEST);

            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void payloadLimitNonPositive() throws IOException {
        new WebSocketSeed.Builder(URI.create("ws://127.0.0.1"), new SilentEventHandler()).maxResponsePayloadSizeInBytes(0);
    }

    @Test
    public void payloadLimit125() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        // Maximum size of 7 bits normal payload length
        WebSocketClientTestUtil.payloadLimit(125);
    }

    @Test
    public void payloadLimit126() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        // Minimum size of 2 Byte extended payload length
        WebSocketClientTestUtil.payloadLimit(126);
    }

    @Test
    public void payloadLimit65535() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        // Maximum size of 2 Byte extended payload length
        WebSocketClientTestUtil.payloadLimit(65535);
    }

    @Test
    public void payloadLimit65536() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        // Minimum size of 8 Byte extended payload length
        WebSocketClientTestUtil.payloadLimit(65536);
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

    @Test
    public void socketBinderTest() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler())
                .socketBinder(new SocketBinder() {
                    @Override
                    public void bind(Socket socket) throws IOException {
                        latch.countDown();
                    }
                }).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(500, TimeUnit.MILLISECONDS);
            assertThat(latch.isUnlockedByCountDown(), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void connectToInvalidPort() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1"), new SilentEventHandler()).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
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
    public void connectToRawSocket() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        final CustomLatch latch = new CustomLatch(1);

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket sock = null;
                Socket socket = null;
                try {
                    sock = new ServerSocket(10001);
                    socket = sock.accept();
                    socket.close();
                } catch (IOException e) {
                    latch.unlockByFailure();
                } finally {
                    try {
                        if (sock != null) {
                            sock.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        th.start();
        latch.await(500, TimeUnit.MILLISECONDS);

        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10001"), new SilentEventHandler()).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(IOException.class));
        } finally {
            th.interrupt();
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void handleUpgradeRequestRejection() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        HttpHeader reject = new HttpHeader.Builder(JettyWebSocketServlet.REJECT_KEY).appendValue("reject").build();
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).headers(Collections.singletonList(reject)).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(500, TimeUnit.MILLISECONDS);
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

    static void callbackCookies(List<HttpCookie> cookies) {
        sCookies = cookies;
        if (sCookieCbLatch != null) {
            sCookieCbLatch.countDown();
        }
    }

    private static CustomLatch sCookieCbLatch;
    private static List<HttpCookie> sCookies;

    static void callbackHeaders(Map<String, List<String>> headers) {
        sHeaders = headers;
        if (sHeaderCbLatch != null) {
            sHeaderCbLatch.countDown();
        }
    }

    private static CustomLatch sHeaderCbLatch;
    private static Map<String, List<String>> sHeaders;

    @Test
    public void connectWithEmptyExtraHeader() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).headers(new ArrayList<HttpHeader>()).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void connectWithHeaders() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        sHeaderCbLatch = new CustomLatch(1);
        HttpHeader single = new HttpHeader.Builder("single").appendValue("value").build();
        HttpHeader multi = new HttpHeader.Builder("multi").appendValue("value1").appendValue("value2").build();
        HttpHeader multi2 = new HttpHeader.Builder("multi").appendValue("value3").build();
        HttpHeader[] headers = {single, multi, multi2};
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).headers(Arrays.asList(headers)).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            sHeaderCbLatch.await(500, TimeUnit.MILLISECONDS);

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
        sCookieCbLatch = new CustomLatch(1);
        HttpHeader cookie = new HttpHeader.Builder("Cookie").appendValue("name1=value1").appendValue("name2=value2").build();
        HttpHeader[] headers = {cookie};
        WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).headers(Arrays.asList(headers)).build();

        WebSocketClientFactory factory = new WebSocketClientFactory();
        WebSocket ws = null;
        try {
            Future<WebSocket> future = factory.openAsync(seed);
            ws = future.get(1000, TimeUnit.MILLISECONDS);
            sCookieCbLatch.await(500, TimeUnit.MILLISECONDS);

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
