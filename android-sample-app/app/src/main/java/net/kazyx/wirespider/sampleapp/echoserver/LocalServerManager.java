/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.sampleapp.echoserver;

import android.util.Log;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.common.extensions.compress.PerMessageDeflateExtension;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class LocalServerManager {
    private static final String TAG = LocalServerManager.class.getSimpleName();

    public static final int PORT = 10000;

    private Server mServer;

    public LocalServerManager() {
        mServer = new Server(PORT);
    }

    public void bootAsync(final ServerLifeCycle listener) {
        WebSocketServlet servlet = new WebSocketServlet() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.setCreator(new WebSocketCreator() {
                    @Override
                    public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
                        return new JettyWebSocketServlet();
                    }
                });
                factory.getExtensionFactory().register("permessage-deflate", PerMessageDeflateExtension.class);
            }
        };

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.addServlet(new ServletHolder(servlet), "/");
        mServer.setHandler(handler);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mServer.start();
                    Log.i(TAG, "Local server launched");
                    listener.onLaunched();
                    mServer.join();
                    Log.i(TAG, "Local server thread end");
                } catch (Exception e) {
                    Log.w(TAG, e);
                } finally {
                    listener.onStopped();
                }
            }
        }).start();
    }

    public void shutdownAsync() {
        try {
            mServer.stop();
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }

    public boolean isRunning() {
        return mServer.isRunning();
    }

    public interface ServerLifeCycle {
        void onLaunched();

        void onStopped();
    }
}
