/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.rfc6455;

import net.kazyx.wirespider.ClientWebSocket;
import net.kazyx.wirespider.FrameRx;
import net.kazyx.wirespider.FrameTx;
import net.kazyx.wirespider.Handshake;
import net.kazyx.wirespider.SessionRequest;
import net.kazyx.wirespider.SocketEngine;
import net.kazyx.wirespider.WsLog;
import net.kazyx.wirespider.delegate.SocketBinder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

/**
 * RFC 6455 based client implementation of WebSocket.
 */
class Rfc6455ClientWebSocket extends ClientWebSocket {
    private static final String TAG = Rfc6455ClientWebSocket.class.getSimpleName();

    private final SessionRequest mReq;
    private final SocketBinder mSocketBinder;

    private final CountDownLatch mConnectLatch = new CountDownLatch(1);

    Rfc6455ClientWebSocket(SessionRequest req, SocketEngine engine, SocketChannel ch) {
        super(req, engine, ch);
        mReq = req;
        mSocketBinder = req.socketBinder();
    }

    @Override
    protected FrameTx newFrameTx() {
        return new Rfc6455Tx(socketChannelProxy(), true);
    }

    @Override
    protected FrameRx newFrameRx(FrameRx.Listener listener) {
        return new Rfc6455Rx(listener, maxResponsePayloadSizeInBytes(), true);
    }

    @Override
    protected Handshake newHandshake() {
        return new Rfc6455Handshake(socketChannelProxy(), true);
    }

    @Override
    protected void onSocketConnected() {
        WsLog.d(TAG, "Start opening handshake");
        handshake().tryUpgrade(remoteUri(), mReq);
    }

    @Override
    protected void onHandshakeFailed() {
        WsLog.d(TAG, "WebSocket handshake failure");
        mConnectLatch.countDown();
    }

    @Override
    protected void onHandshakeCompleted() {
        WsLog.d(TAG, "WebSocket handshake completed");
        mConnectLatch.countDown();
    }

    @Override
    protected void connect() throws IOException, InterruptedException {
        final Socket socket = socketChannel().socket();
        if (mSocketBinder != null) {
            mSocketBinder.bind(socket);
        }
        socket.setTcpNoDelay(true);

        URI uri = remoteUri();
        socketChannel().connect(new InetSocketAddress(uri.getHost(), getPort(uri)));
        socketEngine().register(this, SelectionKey.OP_CONNECT);

        mConnectLatch.await();

        if (!isConnected()) {
            throw new IOException("Socket connection or handshake failure");
        }
    }

    private int getPort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        if ("wss".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        } else {
            return 80;
        }
    }
}
