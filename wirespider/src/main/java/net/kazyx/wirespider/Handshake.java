/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.delegate.HandshakeResponseHandler;
import net.kazyx.wirespider.extension.Extension;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

interface Handshake {
    /**
     * Try to upgrade this connection as WebSocket.
     *
     * @param uri URI of the remote server.
     * @param seed Seed to be used for opening handshake.
     */
    void tryUpgrade(URI uri, SessionRequest seed);

    /**
     * Called when WebSocket handshake response is received.
     *
     * @param data List of received data.
     * @return Remaining (non-header) data.
     * @throws BufferUnsatisfiedException if received data does not contain CRLF. Waiting for the next data.
     * @throws HandshakeFailureException if handshake failure is detected.
     */
    LinkedList<byte[]> onHandshakeResponse(LinkedList<byte[]> data) throws BufferUnsatisfiedException, HandshakeFailureException;

    /**
     * Provide List of accepted WebSocket extensions.
     *
     * @return Copy of accepted WebSocket extensions.
     */
    List<Extension> extensions();

    /**
     * @return Active protocol of this session, or {@code null} if no protocol is defined.
     */
    String protocol();

    /**
     * @param handler Handler to judge handshake response manually.
     */
    void responseHandler(HandshakeResponseHandler handler);
}
