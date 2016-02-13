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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SessionFactoryTest {
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

    @Test
    public void registerDefaultFactory() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).build();

        WebSocketFactory factory = new WebSocketFactory();
        factory.socketEngine().registerFactory(new DefaultSessionFactory(), "ws");

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
}
