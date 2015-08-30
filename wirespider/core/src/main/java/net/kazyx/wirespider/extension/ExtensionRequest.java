/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension;

import net.kazyx.wirespider.http.HttpHeader;

public interface ExtensionRequest {
    /**
     * @return HTTP header for handshake request.
     */
    HttpHeader requestHeader();

    /**
     * @return Extension instance result for this request.
     */
    Extension extension();
}
