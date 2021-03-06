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
import net.kazyx.wirespider.util.IOUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class SubProtocolTest {
    private static TestWebSocketServer server = new TestWebSocketServer(10000);

    private static final String SUBPROTOCOL = "v1.test.protocol";
    private static final String INVALID_SUBPROTOCOL = "dummy.protocol";

    @BeforeClass
    public static void setupClass() throws Exception {
        Base64.setEncoder(new Base64Encoder());
        server.registerSubProtocol(SUBPROTOCOL);
        server.boot();
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        server.shutdown();
    }

    @Test
    public void accepted() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .setProtocols(Collections.singletonList(SUBPROTOCOL))
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            assertThat(ws.isConnected(), is(true));
            assertThat(ws.protocol(), is(SUBPROTOCOL));
        } finally {
            factory.destroy();
        }
    }

    @Test(expected = IOException.class)
    public void rejected() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .setProtocols(Collections.singletonList(INVALID_SUBPROTOCOL))
                .build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) (e.getCause());
            }
        } finally {
            IOUtil.close(ws);
            factory.destroy();
        }
    }

    @Test
    public void multiple() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .setProtocols(Arrays.asList(SUBPROTOCOL, INVALID_SUBPROTOCOL))
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            assertThat(ws.isConnected(), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void customHandlerAccept() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .setProtocols(Collections.singletonList(INVALID_SUBPROTOCOL))
                .setHandshakeHandler(response -> {
                    if (!INVALID_SUBPROTOCOL.equals(response.protocol())) {
                        System.out.println("Response does not contain " + INVALID_SUBPROTOCOL);
                    }
                    return true;
                })
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            assertThat(ws.isConnected(), is(true));
            assertThat(ws.protocol(), is(nullValue()));
        } finally {
            factory.destroy();
        }
    }

    @Test(expected = IOException.class)
    public void customHandlerReject() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler())
                .setProtocols(Collections.singletonList(SUBPROTOCOL))
                .setHandshakeHandler(response -> false)
                .build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) (e.getCause());
            }
        } finally {
            IOUtil.close(ws);
            factory.destroy();
        }
    }
}
