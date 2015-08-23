/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

final class OpCode {
    private OpCode() {
    }

    static final byte CONTINUATION = 0x00;
    static final byte TEXT = 0x01;
    static final byte BINARY = 0x02;
    static final byte CONNECTION_CLOSE = 0x08;
    static final byte PING = 0x09;
    static final byte PONG = 0x0A;
}
