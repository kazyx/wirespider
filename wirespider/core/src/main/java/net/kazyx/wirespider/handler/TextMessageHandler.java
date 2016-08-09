/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.handler;

public interface TextMessageHandler {
    /**
     * Received text message.
     *
     * @param message Received text message
     */
    void onTextMessage(String message);
}
