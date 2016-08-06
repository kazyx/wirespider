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
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        WsLog.e(TAG, "WebSocket handshake failure");
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
     * @param timeout Timeout to complete opening handshake
     * @param unit Timeout unit
     * @throws IOException Failed to open connection.
     */
    void connect(int timeout, TimeUnit unit) throws IOException {
        if (mConnectLatch.getCount() == 0) {
            throw new IOException("ClientWebSocket is not reusable");
        }

        final Socket socket = socketChannel().socket();
        if (mSocketBinder != null) {
            mSocketBinder.bind(socket);
        }
        socket.setTcpNoDelay(true);

        URI uri = remoteUri();

        WsLog.d(TAG, "Start connection");
        socketChannel().connect(new InetSocketAddress(uri.getHost(), getPort(uri)));
        selectorLoop().register(this, SelectionKey.OP_CONNECT);

        try {
            if (timeout == 0) {
                mConnectLatch.await();
            } else {
                if (!mConnectLatch.await(timeout, unit)) {
                    WsLog.e(TAG, "Connection timeout");
                    throw new IOException(String.format(Locale.US, "Connection timeout: %d msec", unit.toMillis(timeout)));
                }
            }
        } catch (InterruptedException e) {
            WsLog.e(TAG, "Connection interrupted");
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
