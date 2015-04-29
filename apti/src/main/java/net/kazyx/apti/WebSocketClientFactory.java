package net.kazyx.apti;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Factory to create WebSocket client connection.
 */
public class WebSocketClientFactory {
    private static final String TAG = WebSocketClientFactory.class.getSimpleName();

    private final AsyncSource mAsync;
    private final SelectorProvider mProvider;

    public WebSocketClientFactory() throws IOException {
        this(Executors.newSingleThreadExecutor());
    }

    public WebSocketClientFactory(ExecutorService executor) throws IOException {
        mProvider = SelectorProvider.provider();
        mAsync = new AsyncSource(executor, mProvider);
    }

    public void destroy() {
        mAsync.destroy();
    }

    public Future<WebSocket> openAsync(URI uri, WebSocketConnection handler) {
        return openAsync(uri, handler, null);
    }

    public Future<WebSocket> openAsync(final URI uri, final WebSocketConnection handler, final List<HttpHeader> headers) {
        return mAsync.mConnectionThreadPool.submit(new Callable<WebSocket>() {
            @Override
            public WebSocket call() throws Exception {
                Logger.d(TAG, "call");
                WebSocket ws = null;
                SocketChannel ch = null;
                try {
                    ws = new WebSocket(mAsync, uri, handler, headers);
                    ch = mProvider.openSocketChannel();
                    ch.configureBlocking(false);
                    ws.connect(ch);
                    return ws;
                } catch (IOException e) {
                    ws.closeInternal();
                    IOUtil.close(ch);
                    throw e;
                }
            }
        });
    }
}