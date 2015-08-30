/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

class ProtocolViolationException extends Exception {
    ProtocolViolationException(String message) {
        super(message);
    }
}
