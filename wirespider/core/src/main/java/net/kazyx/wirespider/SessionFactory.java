/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.nio.channels.SocketChannel;

interface SessionFactory {
    /**
     * Create a new TCP connection wrapper with the given {@link SocketChannel}.
     *
     * @param ch Substance of the connection.
     * @return Newly created {@link Session}.
     */
    Session createNew(SocketChannel ch);
}
