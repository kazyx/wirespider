/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.exception;

public class PayloadOverflowException extends Exception {
    public PayloadOverflowException(String message) {
        super(message);
    }
}
