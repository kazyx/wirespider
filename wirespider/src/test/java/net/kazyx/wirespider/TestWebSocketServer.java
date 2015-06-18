package net.kazyx.wirespider;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.api.extensions.Extension;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class TestWebSocketServer {

    private final Server server;
    private CountDownLatch mStartLatch;
    private CountDownLatch mEndLatch;

    public TestWebSocketServer(int port) {
        server = new Server(port);
    }

    Map<String, Class<? extends Extension>> mExtensions = new HashMap<>();

    public void registerProtocol(String name, Class<? extends Extension> clazz) {
        mExtensions.put(name, clazz);
    }

    public void boot() throws InterruptedException {
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
                for (Map.Entry<String, Class<? extends Extension>> entry : mExtensions.entrySet()) {
                    factory.getExtensionFactory().register(entry.getKey(), entry.getValue());
                }
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

    public void shutdown() throws Exception {
        server.stop();
        mEndLatch.await();
    }
}
