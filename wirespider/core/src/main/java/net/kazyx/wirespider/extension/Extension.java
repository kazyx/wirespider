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

    /**
     * Informs the usage of RSV1, RSV2 and RSV3 bits of this extension.
     * <p>
     * Note that other bits must be {@code false}.<br>
     * As a result, returned value must be expressed by {@code 0b0***0000}
     * </p>
     *
     * @return Reserved bits used by this extension.
     */
    byte reservedBits();
}
