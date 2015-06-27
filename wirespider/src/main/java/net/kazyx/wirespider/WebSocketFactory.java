package net.kazyx.wirespider;

import net.kazyx.wirespider.util.ArgumentCheck;
import net.kazyx.wirespider.util.IOUtil;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Factory of the WebSocket client connections.
 */
public class WebSocketFactory {
    private final AsyncSource mAsync;
    private final SelectorProvider mProvider;

    public WebSocketFactory() throws IOException {
        mProvider = SelectorProvider.provider();
        mAsync = new AsyncSource(mProvider);
    }

    /**
     * Destroy this {@link WebSocketFactory}.<br>
     * Note that any connections created by this instance will be released.
     */
    public synchronized void destroy() {
        mAsync.destroy();
    }

    /**
     * Open WebSocket connection to the specified remote server.
     *
     * @param seed Seed to be used for opening handshake.
     * @return Future of WebSocket instance.
     * @throws java.util.concurrent.RejectedExecutionException if this factory is already destroyed.
     */
    public synchronized Future<WebSocket> openAsync(final SessionRequest seed) {
        ArgumentCheck.rejectNullArgs(seed);

        return mAsync.mConnectionThreadPool.submit(new Callable<WebSocket>() {
            @Override
            public WebSocket call() throws Exception {
                SocketChannel ch = mProvider.openSocketChannel();
                ch.configureBlocking(false);

                ClientWebSocket ws = new ClientWebSocket(seed, mAsync, ch);
                try {
                    ws.connect();
                    return ws;
                } catch (IOException e) {
                    ws.closeNow();
                    IOUtil.close(ch);
                    throw e;
                }
            }
        });
    }
}