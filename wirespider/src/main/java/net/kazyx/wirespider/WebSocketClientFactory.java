package net.kazyx.wirespider;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Factory of the WebSocket client connections.
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
    public void maxResponsePayloadSizeInBytes(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Payload size must be positive value");
        }
        Log.d(TAG, "Max response payload size set to " + size);
        mMaxResponsePayloadSize = size;
    }

    private SocketBinder mSocketBinder;

    /**
     * Set {@link SocketBinder} to be used before opening socket connection.
     *
     * @param binder SocketBinder.
     */
    public void socketBinder(SocketBinder binder) {
        mSocketBinder = binder;
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
     * Equivalent to {@code openAsync(uri, handler, null);}.
     *
     * @param uri     URI of the remote server.
     * @param handler WebSocket connection event handler.
     * @return Future of WebSocket instance.
     * @throws java.util.concurrent.RejectedExecutionException if this instance is already destroyed.
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
     * @throws java.util.concurrent.RejectedExecutionException if this factory is already destroyed.
     */
    public synchronized Future<WebSocket> openAsync(final URI uri, final WebSocketConnection handler, final List<HttpHeader> headers) {
        ArgumentCheck.rejectNullArgs(uri, handler);

        return mAsync.mConnectionThreadPool.submit(new Callable<WebSocket>() {
            @Override
            public WebSocket call() throws Exception {
                SocketChannel ch = mProvider.openSocketChannel();
                ch.configureBlocking(false);

                ClientWebSocket ws = new ClientWebSocket(mAsync, uri, ch, handler, mMaxResponsePayloadSize, headers, mSocketBinder);
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