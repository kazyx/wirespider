/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.delegate.SocketBinder;
import net.kazyx.wirespider.util.WsLog;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

/**
 * Client flavor of WebSocket.
 */
public abstract class ClientWebSocket extends WebSocket {
    private static final String TAG = ClientWebSocket.class.getSimpleName();

    private final SessionRequest mReq;
    private final SocketBinder mSocketBinder;

    private final CountDownLatch mConnectLatch = new CountDownLatch(1);
    private IOException mHandshakeFailure;

    protected ClientWebSocket(SessionRequest req, SelectorLoop loop, SocketChannel ch) {
        super(req, loop, ch);
        mReq = req;
        mSocketBinder = req.socketBinder();
    }

    @Override
    void onSocketConnected() {
        WsLog.d(TAG, "Start opening handshake");
        handshake().tryUpgrade(remoteUri(), mReq);
    }

    @Override
    void onHandshakeFailed(IOException e) {
        WsLog.d(TAG, "WebSocket handshake failure");
        mHandshakeFailure = e;
        mConnectLatch.countDown();
    }

    @Override
    void onHandshakeCompleted() {
        WsLog.d(TAG, "WebSocket handshake completed");
        mConnectLatch.countDown();
    }

    /**
     * Synchronously open client WebSocket connection.
     *
     * @throws IOException Failed to open connection.
     */
    void connect() throws IOException {
        if (mConnectLatch.getCount() == 0) {
            throw new IOException("ClientWebSocket is not reusable");
        }

        final Socket socket = socketChannel().socket();
        if (mSocketBinder != null) {
            mSocketBinder.bind(socket);
        }
        socket.setTcpNoDelay(true);

        URI uri = remoteUri();
        socketChannel().connect(new InetSocketAddress(uri.getHost(), getPort(uri)));
        selectorLoop().register(this, SelectionKey.OP_CONNECT);

        try {
            mConnectLatch.await();
        } catch (InterruptedException e) {
            throw new InterruptedIOException(e.getMessage());
        }

        if (mHandshakeFailure != null) {
            throw mHandshakeFailure;
        }
    }

    private int getPort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        if (WebSocket.WSS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return WebSocket.DEFAULT_WSS_PORT;
        } else {
            return WebSocket.DEFAULT_WS_PORT;
        }
    }
}
