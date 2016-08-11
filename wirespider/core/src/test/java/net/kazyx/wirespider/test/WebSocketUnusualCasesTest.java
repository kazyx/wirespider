/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.test;

import net.kazyx.wirespider.CloseStatusCode;
import net.kazyx.wirespider.FrameRx;
import net.kazyx.wirespider.SessionRequest;
import net.kazyx.wirespider.WebSocket;
import net.kazyx.wirespider.WebSocketFactory;
import net.kazyx.wirespider.util.Base64;
import net.kazyx.wirespider.util.IOUtil;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class WebSocketUnusualCasesTest {
    public static class EventAfterClosed {
        private static Server server = new Server(10000);

        @BeforeClass
        public static void setupClass() throws Exception {
            WebSocketServlet servlet = new WebSocketServlet() {
                @Override
                public void configure(WebSocketServletFactory factory) {
                    factory.setCreator((req, resp) -> {
                        if (req.getHeader(JettyWebSocketServlet.REJECT_KEY) != null) {
                            System.out.println("JettyWebSocket: Reject upgrade");
                            return null;
                        } else {
                            return new JettyWebSocketServlet();
                        }
                    });
                }
            };

            ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
            handler.addServlet(new ServletHolder(servlet), "/");
            server.setHandler(handler);

            Base64.setEncoder(new Base64Encoder());

            mStartLatch = new CountDownLatch(1);
            mEndLatch = new CountDownLatch(1);

            new Thread(() -> {
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
            }).start();

            mStartLatch.await();
            factory = new WebSocketFactory();
        }

        private static WebSocketFactory factory;
        private static CountDownLatch mStartLatch;
        private static CountDownLatch mEndLatch;

        @AfterClass
        public static void teardownClass() throws Exception {
            server.stop();
            System.out.println("Server stopped");
            mEndLatch.await();
            factory.destroy();
        }

        private WebSocket mWs;
        private FrameRx.Listener mListener;

        @Before
        public void setup() throws IOException, InterruptedException, ExecutionException, TimeoutException, NoSuchFieldException, IllegalAccessException {
            SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000")).build();

            mWs = null;
            try {
                Future<WebSocket> future = factory.openAsync(req);
                mWs = future.get(500, TimeUnit.MILLISECONDS);
                assertThat(mWs.isConnected(), is(true));
            } finally {
                IOUtil.close(mWs);
            }

            Field field = WebSocket.class.getDeclaredField("mRxListener");
            field.setAccessible(true);
            mListener = (FrameRx.Listener) field.get(mWs);
        }

        @Test
        public void onPingFrame() {
            mListener.onPingFrame("message");
        }

        @Test
        public void onPongFrame() {
            mListener.onPongFrame("message");
        }

        @Test
        public void onCloseFrame() {
            mListener.onCloseFrame(CloseStatusCode.NORMAL_CLOSURE.asNumber(), "normal closure");
        }

        @Test
        public void onInvalidPayload() {
            mListener.onInvalidPayloadError(new IOException("io error!!"));
        }

        @Test
        public void onBinaryMessage() {
            mListener.onBinaryMessage(ByteBuffer.wrap(new byte[0]));
        }

        @Test
        public void onTextMessage() {
            mListener.onTextMessage("message");
        }

        @Test
        public void onProtocolViolation() {
            mListener.onProtocolViolation();
        }
    }
}
