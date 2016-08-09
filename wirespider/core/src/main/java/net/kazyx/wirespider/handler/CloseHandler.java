/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.handler;

public interface CloseHandler {
    /**
     * WebSocket closed.
     *
     * @param code Close status code
     * @param reason Reason phrase.
     */
    void onClosed(int code, String reason);
}
