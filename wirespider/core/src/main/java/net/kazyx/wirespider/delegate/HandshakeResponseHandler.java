/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.delegate;

import net.kazyx.wirespider.HandshakeResponse;

public interface HandshakeResponseHandler {

    /**
     * Called when fundamental WebSocket handshake is completed.<br>
     * Delegates evaluation of extension and protocol in response header.
     *
     * @param response Response of the handshake.
     * @return Accept this {@link HandshakeResponse} or not.
     */
    boolean onReceived(HandshakeResponse response);
}
