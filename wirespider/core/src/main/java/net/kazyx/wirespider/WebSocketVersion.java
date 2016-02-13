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
    /**
     * @param req Request to be used for opening handshake.
     * @param loop Core selector loop.
     * @param ch Channel to be used for the new connection.
     * @return Newly created un-connected {@link ClientWebSocket} instance.
     */
    ClientWebSocket newClientWebSocket(SessionRequest req, SelectorLoop loop, SocketChannel ch);
}
