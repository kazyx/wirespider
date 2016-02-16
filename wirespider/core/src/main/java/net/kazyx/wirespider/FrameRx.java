/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.Extension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public interface FrameRx {
    /**
     * Called when WebSocket frame data is received.
     *
     * @param data Received data.
     */
    void onDataReceived(ByteBuffer data);

    /**
     * Set WebSocket extensions to be used on this session.
     *
     * @param extensions Negotiated extensions.
     */
    void setExtensions(List<Extension> extensions);

    interface Listener {
        /**
         * Called when received ping control frame.
         *
         * @param message Ping message.
         */
        void onPingFrame(String message);

        /**
         * Called when received pong control frame.
         *
         * @param message Pong message.
         */
        void onPongFrame(String message);

        /**
         * Called when received close control frame or Connection is closed abnormally..
         *
         * @param code Close status code.
         * @param reason Close reason phrase.
         */
        void onCloseFrame(int code, String reason);

        /**
         * Called when a error is thrown while reading message.
         *
         * @param e Exception thrown by message reader.
         */
        void onInvalidPayloadError(IOException e);

        /**
         * Called when received binary message.
         *
         * @param message Received binary message.
         */
        void onBinaryMessage(ByteBuffer message);

        /**
         * Called when received text message.
         *
         * @param message Received text message.
         */
        void onTextMessage(String message);

        /**
         * Called when received message violated WebSocket protocol.
         */
        void onProtocolViolation();

        /**
         * Called when received payload size is larger than maximum response payload size setting.
         */
        void onPayloadOverflow();
    }
}
