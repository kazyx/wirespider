/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

public interface SelectorLoop {
    void destroy();

    /**
     * Register new WebSocket to the Selector.
     *
     * @param ws WebSocket to be registered.
     * @param ops Selector operations.
     */
    void register(WebSocket ws, int ops);

    /**
     * @param factory {@link SessionFactory} to be used for the given URI scheme.
     * @param scheme Scheme of the remote endpoint URI. e.g.) {@code ws} and {@code wss}.
     */
    void registerFactory(SessionFactory factory, String scheme);
}
