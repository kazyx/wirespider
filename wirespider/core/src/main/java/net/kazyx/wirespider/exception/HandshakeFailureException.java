/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.exception;

import java.io.IOException;

public class HandshakeFailureException extends IOException {
    public HandshakeFailureException(String message) {
        super(message);
    }

    public HandshakeFailureException(Throwable th) {
        super(th);
    }
}
