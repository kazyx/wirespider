package net.kazyx.apti;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Client specific implementation of WebSocket.
 */
class ClientWebSocket extends WebSocket {
    private static final String TAG = ClientWebSocket.class.getSimpleName();

    private final List<HttpHeader> mRequestHeaders;
    private final SocketBinder mSocketBinder;

    private final CountDownLatch mConnectLatch = new CountDownLatch(1);

    ClientWebSocket(AsyncSource async, URI uri, SocketChannel ch, WebSocketConnection handler, int maxPayload, List<HttpHeader> extraHeaders, SocketBinder binder) {
        super(async, uri, ch, handler, maxPayload, true);
        mRequestHeaders = extraHeaders;
        mSocketBinder = binder;
    }

    @Override
    void onSocketConnected() {
        AptiLog.d(TAG, "Start opening handshake");
        handshake().tryUpgrade(remoteUri(), mRequestHeaders);
    }

    @Override
    void onHandshakeFailed() {
        AptiLog.d(TAG, "WebSocket handshake failure");
        mConnectLatch.countDown();
    }

    @Override
    void onHandshakeCompleted() {
        AptiLog.d(TAG, "WebSocket handshake completed");
        mConnectLatch.countDown();
    }

    /**
     * Synchronously open WebSocket connection.
     *
     * @throws IOException          Failed to open connection.
     * @throws InterruptedException Awaiting thread interrupted.
     */
    void connect() throws IOException, InterruptedException {
        final Socket socket = socketChannel().socket();
        if (mSocketBinder != null) {
            mSocketBinder.bind(socket);
        }
        socket.setTcpNoDelay(true);

        URI uri = remoteUri();
        socketChannel().connect(new InetSocketAddress(uri.getHost(), (uri.getPort() != -1) ? uri.getPort() : 80));
        asyncSource().register(this, SelectionKey.OP_CONNECT);

        mConnectLatch.await();

        if (!isConnected()) {
            throw new IOException("Socket connection or handshake failure");
        }
    }
}
