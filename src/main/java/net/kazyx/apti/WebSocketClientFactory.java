package net.kazyx.apti;

import javax.net.SocketFactory;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Factory to create WebSocket client connection.
 */
public class WebSocketClientFactory {
    private final AsyncSource mAsync;

    public WebSocketClientFactory(AsyncSource async) {
        mAsync = async;
    }

    public Future<WebSocket> open(URI uri, WebSocketConnection handler) {
        return open(uri, handler, null);
    }

    public Future<WebSocket> open(final URI uri, final WebSocketConnection handler, final List<HttpHeader> headers) {
        return mAsync.mConnectionThreadPool.submit(new Callable<WebSocket>() {
            @Override
            public WebSocket call() throws Exception {
                WebSocket ws = new WebSocket(mAsync, uri, handler, headers);
                ws.connect();
                return ws;
            }
        });
    }

    static SocketFactory sFactory = null;

    /**
     * Specify SocketFactory to be used for opening WebSocket connection.
     *
     * @param factory SocketFactory to be used for opening WebSocket connection.
     */
    public static void setSocketFactory(SocketFactory factory) {
        sFactory = factory;
    }
}