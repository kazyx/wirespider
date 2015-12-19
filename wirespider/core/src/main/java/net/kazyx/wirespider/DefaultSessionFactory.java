/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.nio.channels.SelectionKey;

/**
 * Factory to create unsecured TCP connections.
 */
class DefaultSessionFactory implements SessionFactory {
    @Override
    public DefaultSession createNew(SelectionKey key) {
        return new DefaultSession(key);
    }
}
