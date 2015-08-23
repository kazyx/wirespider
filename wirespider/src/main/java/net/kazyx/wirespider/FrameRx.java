/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.compression.PerMessageCompression;

import java.util.LinkedList;

interface FrameRx {
    /**
     * Called when WebSocket frame data is received.
     *
     * @param data List of received data.
     */
    void onDataReceived(LinkedList<byte[]> data);

    void decompressMessagesWith(PerMessageCompression compression);

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
         * Called when received binary message.
         *
         * @param message Received binary message.
         */
        void onBinaryMessage(byte[] message);

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
