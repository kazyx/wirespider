package net.kazyx.apti;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CountDownLatch;

class ClientWebSocket extends WebSocket {
    private final List<HttpHeader> mRequestHeaders;

    private final CountDownLatch mConnectLatch = new CountDownLatch(1);

    ClientWebSocket(AsyncSource async, URI uri, SocketChannel ch, WebSocketConnection handler, List<HttpHeader> extraHeaders) {
        super(async, uri, ch, handler, true);
        mRequestHeaders = extraHeaders;
    }

    @Override
    void onSocketConnected() {
        getHandshake().tryUpgrade(getRemoteURI(), mRequestHeaders);
    }

    @Override
    void onHandshakeFailed() {
        mConnectLatch.countDown();
    }

    @Override
    void onHandshakeCompleted() {
        // Logger.d(TAG, "WebSocket handshake succeed!!");
        mConnectLatch.countDown();
    }

    /**
     * Synchronously openAsync WebSocket connection.
     *
     * @throws IOException          Failed to openAsync connection.
     * @throws InterruptedException Awaiting thread interrupted.
     */
    void connect() throws IOException, InterruptedException {
        final Socket socket = getSocketChannel().socket();
        socket.setTcpNoDelay(true);
        // TODO bind local address here.

        URI mURI = getRemoteURI();
        getSocketChannel().connect(new InetSocketAddress(mURI.getHost(), (mURI.getPort() != -1) ? mURI.getPort() : 80));
        getAsync().register(this, SelectionKey.OP_CONNECT);

        mConnectLatch.await();

        if (!isConnected()) {
            throw new IOException("Socket connection or handshake failure");
        }
    }
}
