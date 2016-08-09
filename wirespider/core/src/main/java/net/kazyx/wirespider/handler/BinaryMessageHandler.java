/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.handler;

public interface BinaryMessageHandler {
    /**
     * Received binary message.
     *
     * @param message Received binary message
     */
    void onBinaryMessage(byte[] message);
}
