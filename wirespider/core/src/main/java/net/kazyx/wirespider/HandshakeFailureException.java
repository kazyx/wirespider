/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

class HandshakeFailureException extends Exception {
    HandshakeFailureException(String message) {
        super(message);
    }

    HandshakeFailureException(Throwable th) {
        super(th);
    }
}
