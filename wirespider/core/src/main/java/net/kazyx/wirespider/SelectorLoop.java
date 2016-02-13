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
     * @param factory {@link SessionFactory}to use for the specified URI scheme.
     * @param scheme Scheme to use the {@link SessionFactory} to create a {@link Session}
     */
    void registerFactory(SessionFactory factory, String scheme);
}
