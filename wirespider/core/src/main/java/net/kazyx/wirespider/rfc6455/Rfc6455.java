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
import net.kazyx.wirespider.SessionRequest;
import net.kazyx.wirespider.SocketEngine;
import net.kazyx.wirespider.WebSocketVersion;

import java.nio.channels.SocketChannel;

public class Rfc6455 implements WebSocketVersion {
    @Override
    public ClientWebSocket newClientWebSocket(SessionRequest req, SocketEngine engine, SocketChannel ch) {
        return new Rfc6455ClientWebSocket(req, engine, ch);
    }
}
