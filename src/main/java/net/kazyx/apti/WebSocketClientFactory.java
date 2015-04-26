package net.kazyx.apti;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
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
    private final AsyncSource mAsync;

    public WebSocketClientFactory() {
        this(Executors.newSingleThreadExecutor());
    }

    public WebSocketClientFactory(ExecutorService executor) {
        mAsync = new AsyncSource(executor);
        mSelectorThread.start();
    }

    public void destroy() {
        mSelectorThread.interrupt();
        mAsync.destroy();
    }

    public Future<WebSocket> openAsync(URI uri, WebSocketConnection handler) {
        return openAsync(uri, handler, null);
    }

    public Future<WebSocket> openAsync(final URI uri, final WebSocketConnection handler, final List<HttpHeader> headers) {
        return mAsync.mConnectionThreadPool.submit(new Callable<WebSocket>() {
            @Override
            public WebSocket call() throws Exception {
                WebSocket ws = null;
                try {
                    ws = new WebSocket(mAsync, uri, handler, headers);
                    ws.connect();
                    return ws;
                } catch (IOException e) {
                    ws.closeInternal();
                    throw e;
                }
            }
        });
    }

    final Thread mSelectorThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                Selector selector = SelectorProvider.provider().openSelector();
                while (selector.select() > 0) {
                    for (SelectionKey key : selector.selectedKeys()) {
                        SelectionHandler handler = (SelectionHandler) key.attachment();
                        handler.onSelected(key);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    });
}