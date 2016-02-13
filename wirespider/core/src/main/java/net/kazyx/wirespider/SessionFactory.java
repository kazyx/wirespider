/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface SessionFactory {
    /**
     * Create a new TCP connection wrapper with the given {@link SelectionKey}.
     *
     * @param key Selection key of the connection.
     * @return Newly created {@link Session}.
     * @throws IOException If failed to create new session.
     */
    Session createNew(SelectionKey key) throws IOException;
}
