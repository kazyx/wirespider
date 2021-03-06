/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.delegate;

import java.io.IOException;
import java.net.Socket;

public interface SocketBinder {
    /**
     * Bind socket to the specified local address or network interface.
     *
     * @param socket Unbound socket.
     * @throws IOException if failed to bind the socket.
     */
    void bind(Socket socket) throws IOException;
}
