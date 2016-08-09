/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.http.HttpHeader;
import net.kazyx.wirespider.util.Base64;
import net.kazyx.wirespider.util.IOUtil;
import net.kazyx.wirespider.util.WsLog;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

public class WebSocketClientTest {
    private static TestWebSocketServer server = new TestWebSocketServer(10000);

    @BeforeClass
    public static void setupClass() throws Exception {
        Base64.setEncoder(new Base64Encoder());
        server.boot();
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        server.shutdown();
    }

    @Before
    public void setup() throws Exception {
        sHeaders = null;
        sCookies = null;
        sPingFrameLatch = null;
        sCloseFrameLatch = null;
        sCookieCbLatch = null;
        sHeaderCbLatch = null;
        WsLog.logLevel(WsLog.Level.DEBUG);
    }

    @After
    public void teardown() throws Exception {
    }

    @Test
    public void connectJetty9() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000")).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            assertThat(ws.isConnected(), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void connectJetty9Sync() throws IOException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000")).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.open(req)) {
            assertThat(ws.isConnected(), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void nothingHappensAfterClosed() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        final CustomLatch latch = new CustomLatch(2);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setTextHandler(message -> latch.countDown())
                .setBinaryHandler(message -> latch.countDown())
                .setPongHandler(message -> latch.countDown())
                .setCloseHandler((code, reason) -> latch.countDown())
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            ws.close();
            Thread.sleep(200);
            ws.sendTextMessageAsync("test");
            ws.sendBinaryMessageAsync("test".getBytes("UTF-8"));
            ws.sendPingAsync("ping");
            ws.closeAsync();
            Thread.sleep(500);
            assertThat(latch.getCount(), is(1L));
        } finally {
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
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000")).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            ws.closeAsync();
            assertThat(sCloseFrameLatch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void suddenShutdownOfSocketEngine() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        final CustomLatch latch = new CustomLatch(1);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setCloseHandler((code, reason) -> {
                            if (code == CloseStatusCode.ABNORMAL_CLOSURE.statusCode) {
                                latch.countDown();
                            } else {
                                latch.unlockByFailure();
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            factory.destroy();
            assertThat(latch.awaitSuccess(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void shutdownSoonAfterCloseSent() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setCloseHandler((code, reason) -> latch.countDown())
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            ws.closeAsync(CloseStatusCode.GOING_AWAY, "Going away");
            factory.destroy();
            assertThat(latch.await(200, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByCountDown(), is(true));
            assertThat(ws.isConnected(), is(false));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void closedByAbnormalClosure() throws IOException, InterruptedException, ExecutionException, TimeoutException, NoSuchFieldException, IllegalAccessException {
        final CustomLatch latch = new CustomLatch(1);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setCloseHandler((code, reason) -> {
                            if (code == CloseStatusCode.ABNORMAL_CLOSURE.asNumber()) {
                                latch.countDown();
                            } else {
                                latch.unlockByFailure();
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            Field f = WebSocket.class.getDeclaredField("mRxListener");
            f.setAccessible(true);
            FrameRx.Listener listener = (FrameRx.Listener) f.get(ws);
            listener.onCloseFrame(CloseStatusCode.ABNORMAL_CLOSURE.asNumber(), "abnormal");

            assertThat(latch.await(200, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByCountDown(), is(true));
            assertThat(ws.isConnected(), is(false));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void closedByInvalidPayload() throws IOException, InterruptedException, ExecutionException, TimeoutException, NoSuchFieldException, IllegalAccessException {
        final CustomLatch latch = new CustomLatch(1);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setCloseHandler((code, reason) -> {
                            if (code == CloseStatusCode.INVALID_FRAME_PAYLOAD_DATA.asNumber()) {
                                latch.countDown();
                            } else {
                                latch.unlockByFailure();
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            Field f = WebSocket.class.getDeclaredField("mRxListener");
            f.setAccessible(true);
            FrameRx.Listener listener = (FrameRx.Listener) f.get(ws);
            listener.onInvalidPayloadError(new IOException("invalid payload error!!"));

            assertThat(latch.await(200, TimeUnit.MILLISECONDS), is(true));
            assertThat(latch.isUnlockedByCountDown(), is(true));
            assertThat(ws.isConnected(), is(false));
        } finally {
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
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000")).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            ws.closeAsync();
            ws.sendTextMessageAsync(JettyWebSocketServlet.ASSERT_REQUEST);
            assertThat(sAssertLatch.await(500, TimeUnit.MILLISECONDS), is(false));
        } finally {
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
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setCloseHandler((code, reason) -> {
                    if (sPingFrameLatch != null) {
                        sPingFrameLatch.unlockByFailure();
                    }
                }).setPongHandler(message -> {
                            if (message.equals(msg)) {
                                sPingFrameLatch.countDown();
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            ws.sendPingAsync(msg);
            assertThat(sPingFrameLatch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void nothingInvokedByDefaultOnPong() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        sPingFrameLatch = new CustomLatch(2);
        final String msg = "ping";
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setCloseHandler((code, reason) -> {
                            if (sPingFrameLatch != null) {
                                sPingFrameLatch.unlockByFailure();
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            ws.sendPingAsync(msg);
            assertThat(sPingFrameLatch.await(500, TimeUnit.MILLISECONDS), is(false));
            assertThat(sPingFrameLatch.getCount(), is(1L));
        } finally {
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
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setCloseHandler((code, reason) -> {
                            if (sPongFrameLatch != null) {
                                sPongFrameLatch.unlockByFailure();
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            ws.sendTextMessageAsync(JettyWebSocketServlet.PING_REQUEST);

            assertThat(sPongFrameLatch.await(1000, TimeUnit.MILLISECONDS), is(true));
            assertThat(ws.isConnected(), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void onCloseIsCalledIfFactoryIsDestroyed() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setCloseHandler((code, reason) -> {
                            System.out.println("WebSocketHandler onClosed");
                            if (code == CloseStatusCode.ABNORMAL_CLOSURE.asNumber()) {
                                latch.countDown();
                            } else {
                                throw new IllegalStateException("Invalid close status code");
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            factory.destroy();
            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void x100ParallelConnections() throws IOException, InterruptedException {
        final WebSocketFactory factory = new WebSocketFactory();
        ExecutorService es = Executors.newCachedThreadPool();

        final Set<WebSocket> set = new HashSet<>();
        int NUM_CONNECTIONS = 100;

        WsLog.logLevel(WsLog.Level.ERROR);

        final CountDownLatch latch = new CountDownLatch(NUM_CONNECTIONS);
        try {
            for (int i = 0; i < NUM_CONNECTIONS; i++) {
                es.submit(() -> {
                    try {
                        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000")).build();
                        WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS);
                        set.add(ws);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        throw new RuntimeException(e);
                    }
                    latch.countDown();
                });
            }

            assertThat(latch.await(5000, TimeUnit.MILLISECONDS), is(true));
        } finally {
            set.forEach(IOUtil::close);
            es.shutdownNow();
            factory.destroy();
        }
    }

    private static final String MESSAGE = "echo: qwertyuiop@[asdfghjkkl:;zxcvbnm,,.";

    @Test
    public void x100000TextMessagesEcho() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final int NUM_MESSAGES = 100000;
        final CountDownLatch latch = new CountDownLatch(NUM_MESSAGES);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setTextHandler(message -> {
                            // System.out.println("onTextMessage");
                            if (message.equals(MESSAGE)) {
                                latch.countDown();
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(10000, TimeUnit.MILLISECONDS)) {
            System.out.println("Start sending messages");
            for (int i = 0; i < NUM_MESSAGES; i++) {
                ws.sendTextMessageAsync(MESSAGE);
            }

            assertThat(latch.await(40000, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void handleCloseOpcode() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setCloseHandler((code, reason) -> {
                            System.out.println("onClosed: " + code);
                            if (code == CloseStatusCode.NORMAL_CLOSURE.asNumber()) {
                                latch.countDown();
                            }
                        }
                ).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            ws.sendTextMessageAsync(JettyWebSocketServlet.CLOSE_REQUEST);

            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void payloadLimitNonPositive() throws IOException {
        new SessionRequest.Builder(URI.create("ws://127.0.0.1")).setMaxResponsePayloadSizeInBytes(0);
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
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setSocketBinder(socket -> latch.countDown())
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            assertThat(latch.isUnlockedByCountDown(), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void connectToInvalidPort() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1")).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req, false).get(10000, TimeUnit.MILLISECONDS)) {
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(IOException.class)));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void connectToRawSocket() throws InterruptedException, ExecutionException, TimeoutException, IOException {
        final CustomLatch latch = new CustomLatch(1);

        Thread th = new Thread(() -> {
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
        });
        th.start();
        latch.await(500, TimeUnit.MILLISECONDS);

        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10001")).build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req, false).get(1000, TimeUnit.MILLISECONDS)) {
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(IOException.class));
        } finally {
            th.interrupt();
            factory.destroy();
        }
    }

    @Test
    public void handleUpgradeRequestRejection() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        HttpHeader reject = new HttpHeader.Builder(JettyWebSocketServlet.REJECT_KEY).appendValue("reject").build();
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setHeaders(Collections.singletonList(reject))
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(500, TimeUnit.MILLISECONDS)) {
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(IOException.class)));
        } finally {
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
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setHeaders(new ArrayList<>())
                .build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS);
        } finally {
            IOUtil.close(ws);
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
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setHeaders(Arrays.asList(headers))
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            sHeaderCbLatch.await(500, TimeUnit.MILLISECONDS);

            assertThat(sHeaders.keySet(), hasItems("single", "multi"));
            assertThat(sHeaders.get("single"), is(contains("value")));
            assertThat(sHeaders.get("multi"), is(contains("value1,value2", "value3")));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void connectWithCookies() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        sCookieCbLatch = new CustomLatch(1);
        HttpHeader cookie = new HttpHeader.Builder("Cookie").appendValue("name1=value1").appendValue("name2=value2").build();
        HttpHeader[] headers = {cookie};
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                .setHeaders(Arrays.asList(headers))
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
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
            factory.destroy();
        }
    }
}
