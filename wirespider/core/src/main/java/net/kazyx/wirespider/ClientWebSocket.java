/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public abstract class ClientWebSocket extends WebSocket {
    protected ClientWebSocket(SessionRequest req, SocketEngine engine, SocketChannel ch) {
        super(req, engine, ch);
    }

    /**
     * Synchronously open WebSocket connection.
     *
     * @throws IOException Failed to open connection.
     * @throws InterruptedException Awaiting thread interrupted.
     */
    protected abstract void connect() throws IOException, InterruptedException;
}
