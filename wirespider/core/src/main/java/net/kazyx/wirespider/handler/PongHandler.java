/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.handler;

import net.kazyx.wirespider.WebSocket;

public interface PongHandler {
    /**
     * Received Pong frame.<br>
     * If you uses {@link WebSocket#sendPingAsync(String)}, override this method to handle Pong frames.
     *
     * @param message Pong message. This should be equal to what you've sent with Ping frame.
     */
    void onPong(String message);
}
