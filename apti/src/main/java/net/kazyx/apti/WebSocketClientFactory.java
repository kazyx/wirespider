package net.kazyx.apti;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Factory to create WebSocket client connection.
 */
public class WebSocketClientFactory {
    private static final String TAG = WebSocketClientFactory.class.getSimpleName();

    private final AsyncSource mAsync;
    private final SelectorProvider mProvider;

    public WebSocketClientFactory() throws IOException {
        mProvider = SelectorProvider.provider();
        mAsync = new AsyncSource(mProvider);
    }

    private int mMaxResponsePayloadSize = 65536;

    /**
     * Set maximum size of response payload.
     *
     * @param size Maximum size in bytes.
     */
    public void setMaxResponsePayloadSizeInBytes(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Payload size must be positive value");
        }
        AptiLog.d(TAG, "Max response payload size set to " + size);
        mMaxResponsePayloadSize = size;
    }

    /**
     * Destroy this {@link WebSocketClientFactory}.<br>
     * Note that any connections created by this instance will be released.
     */
    public synchronized void destroy() {
        mAsync.destroy();
    }

    /**
     * Open WebSocket connection to the specified remote server.<br>
     * Equivalent to {@code open(uri, handler, null);}.
     *
     * @param uri     URI of the remote server.
     * @param handler WebSocket connection event handler.
     * @return Future of WebSocket instance.
     */
    public synchronized Future<WebSocket> openAsync(URI uri, WebSocketConnection handler) {
        return openAsync(uri, handler, null);
    }

    /**
     * Open WebSocket connection to the specified remote server.
     *
     * @param uri     URI of the remote server.
     * @param handler WebSocket connection event handler.
     * @param headers Additional HTTP header to be inserted to opening request.
     * @return Future of WebSocket instance.
     * @throws IllegalStateException                           if this instance is already destroyed.
     * @throws java.util.concurrent.RejectedExecutionException if this factory is already destroyed.
     */
    public synchronized Future<WebSocket> openAsync(final URI uri, final WebSocketConnection handler, final List<HttpHeader> headers) {
        ArgumentCheck.rejectNullArgs(uri, handler);

        if (!mAsync.isAlive()) {
            throw new IllegalStateException("This WebSocketClientFactory is already destroyed.");
        }

        return mAsync.mConnectionThreadPool.submit(new Callable<WebSocket>() {
            @Override
            public WebSocket call() throws Exception {
                ClientWebSocket ws = null;
                SocketChannel ch = null;
                try {
                    ch = mProvider.openSocketChannel();
                    ch.configureBlocking(false);

                    ws = new ClientWebSocket(mAsync, uri, ch, handler, mMaxResponsePayloadSize, headers);
                    ws.connect();
                    return ws;
                } catch (IOException e) {
                    if (ws != null) {
                        ws.closeNow();
                    }
                    IOUtil.close(ch);
                    throw e;
                }
            }
        });
    }
}