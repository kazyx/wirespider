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

/**
 * Factory to create unsecured TCP connections.
 */
class DefaultSessionFactory implements SessionFactory {
    @Override
    public DefaultSession createNew(SocketChannel ch) {
        return new DefaultSession(ch);
    }
}
