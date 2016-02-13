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
import net.kazyx.wirespider.WebSocketVersion;

import java.nio.channels.SocketChannel;

public class Rfc6455 implements WebSocketVersion {
    @Override
    public ClientWebSocket newClientWebSocket(SessionRequest req, SelectorLoop loop, SocketChannel ch) {
        return new ClientWebSocket(req, loop, ch) {
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
        };
    }
}
