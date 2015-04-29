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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WebSocketClientFactoryTest {
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
            Future<net.kazyx.apti.WebSocket> future = factory.openAsync(URI.create("ws://127.0.0.1:10000"), new WebSocketConnection() {
                @Override
                public void onTextMessage(String message) {

                }

                @Override
                public void onBinaryMessage(byte[] message) {

                }

                @Override
                public void onClosed() {

                }
            });
            ws = future.get(1000000, TimeUnit.MILLISECONDS);
            Assert.assertTrue(ws.isConnected());
        } finally {
            if (ws != null) {
                System.out.println("Test finished. Now close WebSocket");
                ws.closeAsync();
            }
            factory.destroy();
        }
    }
}
