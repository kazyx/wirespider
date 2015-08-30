/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

/**
 * WebSocket event handler.
 */
public abstract class WebSocketHandler {
    /**
     * Received text message.
     *
     * @param message Received text message
     */
    public abstract void onTextMessage(String message);

    /**
     * Received binary message.
     *
     * @param message Received binary message
     */
    public abstract void onBinaryMessage(byte[] message);

    /**
     * Received Pong frame.<br>
     * If you uses {@link WebSocket#sendPingAsync(String)}, override this method to handle Pong frames.
     *
     * @param message Pong message. This should be equal to what you've sent with Ping frame.
     */
    public void onPong(String message) {
        // Nothing to do by default.
    }

    /**
     * WebSocket closed.
     *
     * @param code Close status code
     * @param reason Reason phrase.
     */
    public abstract void onClosed(int code, String reason);
}
