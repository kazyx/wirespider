package net.kazyx.wirespider;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(Enclosed.class)
public class WebSocketUnusualCasesTest {
    public static class EventAfterClosed {
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

            Base64.encoder(new Base64Encoder());

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
            SessionRequest seed = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler()).build();

            mWs = null;
            try {
                Future<WebSocket> future = factory.openAsync(seed);
                mWs = future.get(500, TimeUnit.MILLISECONDS);
                assertThat(mWs.isConnected(), is(true));
            } finally {
                if (mWs != null) {
                    mWs.closeNow();
                }
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
        public void onBinaryMessage() {
            mListener.onBinaryMessage(new byte[0]);
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

    public static class UseDestroyedAsyncSource {
        private static AsyncSource sAsync;

        @BeforeClass
        public static void setup() throws IOException {
            sAsync = new AsyncSource(SelectorProvider.provider());
            sAsync.destroy();
        }

        @Test
        public void safeAsync() throws InterruptedException {
            final CustomLatch latch = new CustomLatch(1);
            sAsync.safeAsync(new Runnable() {
                @Override
                public void run() {
                    latch.unlockByFailure();
                }
            });
            assertThat(latch.await(200, TimeUnit.MILLISECONDS), is(false));
        }
    }
}
