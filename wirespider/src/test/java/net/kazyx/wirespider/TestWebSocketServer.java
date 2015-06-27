package net.kazyx.wirespider;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.common.extensions.compress.PerMessageDeflateExtension;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class TestWebSocketServer {

    private final Server server;
    private CountDownLatch mStartLatch;
    private CountDownLatch mEndLatch;

    public TestWebSocketServer(int port) {
        server = new Server(port);
    }

    public enum Extension {
        DEFLATE("permessage-deflate", PerMessageDeflateExtension.class);

        String name;
        Class<? extends org.eclipse.jetty.websocket.api.extensions.Extension> clazz;

        Extension(String name, Class<? extends org.eclipse.jetty.websocket.api.extensions.Extension> clazz) {
            this.name = name;
            this.clazz = clazz;
        }
    }

    private final List<Extension> mExtensions = new ArrayList<>();

    public void registerExtension(Extension extension) {
        mExtensions.add(extension);
    }

    private final List<String> mProtocols = new ArrayList<>();

    public void registerSubProtocol(String name) {
        mProtocols.add(name);
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
                            List<String> reqProtocols = req.getSubProtocols();
                            if (reqProtocols.size() == 0) {
                                return new JettyWebSocketServlet();
                            } else {
                                System.out.println("JettyWebSocket: subprotocol request: " + reqProtocols);
                                for (String protocol : reqProtocols) {
                                    if (mProtocols.contains(protocol)) {
                                        resp.setAcceptedSubProtocol(protocol);
                                        return new JettyWebSocketServlet();
                                    }
                                }
                                return new JettyWebSocketServlet();
                            }
                        }
                    }
                });
                for (Extension extension : mExtensions) {
                    factory.getExtensionFactory().register(extension.name, extension.clazz);
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
