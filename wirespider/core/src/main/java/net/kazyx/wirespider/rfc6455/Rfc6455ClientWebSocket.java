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
import net.kazyx.wirespider.SelectorLoop;
import net.kazyx.wirespider.SessionRequest;

import java.nio.channels.SocketChannel;

/**
 * RFC 6455 based client implementation of WebSocket.
 */
class Rfc6455ClientWebSocket extends ClientWebSocket {
    Rfc6455ClientWebSocket(SessionRequest req, SelectorLoop engine, SocketChannel ch) {
        super(req, engine, ch);
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
}
