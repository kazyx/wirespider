/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension;

/**
 * WebSocket extension.
 */
public interface Extension {
    /**
     * @return Name of extension method.
     */
    String name();

    /**
     * @param parameters Handshake response for this extension.
     * @return Accept the response or not.
     */
    boolean accept(String[] parameters);

    /**
     * @return Payload filter of this extension.
     */
    PayloadFilter filter();
}
