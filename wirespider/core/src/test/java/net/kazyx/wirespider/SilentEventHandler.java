/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

public class SilentEventHandler extends WebSocketHandler {
    @Override
    public void onTextMessage(String message) {
    }

    @Override
    public void onBinaryMessage(byte[] message) {
    }

    @Override
    public void onClosed(int code, String reason) {
    }
}
