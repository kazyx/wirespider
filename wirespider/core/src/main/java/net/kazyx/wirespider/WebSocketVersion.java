/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.nio.channels.SocketChannel;

public interface WebSocketVersion {
    ClientWebSocket newClientWebSocket(SessionRequest req, SocketEngine engine, SocketChannel ch);
}
