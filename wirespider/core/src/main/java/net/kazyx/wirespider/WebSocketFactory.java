/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.rfc6455.Rfc6455;
import net.kazyx.wirespider.secure.SecureSessionFactory;
import net.kazyx.wirespider.util.ArgumentCheck;
import net.kazyx.wirespider.util.IOUtil;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Factory of the WebSocket client connections.
 */
public class WebSocketFactory {
    private final SelectorProvider mProvider;
    private final SelectorLoop mSelectorLoop;
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    public WebSocketFactory() throws IOException {
        mProvider = SelectorProvider.provider();
        mSelectorLoop = new SessionManager(mProvider);
    }

    /**
     * Destroy this {@link WebSocketFactory}.<br>
     * Note that any connections created by this instance will be released.
     */
    public synchronized void destroy() {
        mSelectorLoop.destroy();
        mExecutor.shutdown();
    }

    private WebSocketSpec mSpec = new Rfc6455();

    /**
     * Open client WebSocket connection to the remote server.
     *
     * @param req Request to be used for opening handshake.
     * @return Future of WebSocket instance.
     * @throws IOException Failed to open connection.
     */
    public WebSocket open(SessionRequest req) throws IOException {
        ArgumentCheck.rejectNullArgs(req);
        return openSync(req);
    }

    /**
     * Open WebSocket connection to the remote server asynchronously.
     *
     * @param req Request to be used for opening handshake.
     * @return Future of WebSocket instance.
     * @throws java.util.concurrent.RejectedExecutionException if this factory is already destroyed.
     */
    public synchronized Future<WebSocket> openAsync(final SessionRequest req) {
        ArgumentCheck.rejectNullArgs(req);

        return mExecutor.submit(new Callable<WebSocket>() {
            @Override
            public WebSocket call() throws Exception {
                return openSync(req);
            }
        });
    }

    private WebSocket openSync(SessionRequest req) throws IOException {
        SocketChannel ch = mProvider.openSocketChannel();
        ch.configureBlocking(false);
        ClientWebSocket ws = mSpec.newClientWebSocket(req, mSelectorLoop, ch);
        try {
            ws.connect();
            return ws;
        } catch (IOException e) {
            ws.closeNow();
            IOUtil.close(ch);
            throw e;
        }
    }

    /**
     * Set a specification of WebSocket.
     *
     * @param spec {@link WebSocketSpec} to be used by this factory. {@link Rfc6455} is used by default.
     */
    public void setSpec(WebSocketSpec spec) {
        ArgumentCheck.rejectNull(spec);
        mSpec = spec;
    }

    /**
     * Set custom {@link SSLContext} for secure WebSocket connection.<br>
     * If nothing is set, default {@link SSLContext} is used.
     *
     * @param context Non default {@link SSLContext}.
     */
    public static void setSslContext(SSLContext context) {
        SecureSessionFactory.setSslContext(context);
    }
}
