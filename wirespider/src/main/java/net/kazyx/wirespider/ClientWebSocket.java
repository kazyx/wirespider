/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.delegate.SocketBinder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

/**
 * Client specific implementation of WebSocket.
 */
class ClientWebSocket extends WebSocket {
    private static final String TAG = ClientWebSocket.class.getSimpleName();

    private final SessionRequest mSeed;
    private final SocketBinder mSocketBinder;

    private final CountDownLatch mConnectLatch = new CountDownLatch(1);

    ClientWebSocket(SessionRequest seed, SocketEngine engine, SocketChannel ch) {
        super(seed, engine, ch);
        mSeed = seed;
        mSocketBinder = seed.socketBinder();
    }

    @Override
    FrameTx newFrameTx() {
        return new Rfc6455Tx(socketChannelProxy(), true);
    }

    @Override
    FrameRx newFrameRx(FrameRx.Listener listener) {
        return new Rfc6455Rx(listener, maxResponsePayloadSizeInBytes(), true);
    }

    @Override
    Handshake newHandshake() {
        return new Rfc6455Handshake(socketChannelProxy(), true);
    }

    @Override
    void onSocketConnected() {
        WsLog.d(TAG, "Start opening handshake");
        handshake().tryUpgrade(remoteUri(), mSeed);
    }

    @Override
    void onHandshakeFailed() {
        WsLog.d(TAG, "WebSocket handshake failure");
        mConnectLatch.countDown();
    }

    @Override
    void onHandshakeCompleted() {
        WsLog.d(TAG, "WebSocket handshake completed");
        mConnectLatch.countDown();
    }

    /**
     * Synchronously open WebSocket connection.
     *
     * @throws IOException Failed to open connection.
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
        socketEngine().register(this, SelectionKey.OP_CONNECT);

        mConnectLatch.await();

        if (!isConnected()) {
            throw new IOException("Socket connection or handshake failure");
        }
    }
}
