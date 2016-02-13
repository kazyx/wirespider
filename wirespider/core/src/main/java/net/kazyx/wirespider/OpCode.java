/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

public final class OpCode {
    private OpCode() {
    }

    public static final byte CONTINUATION = 0x00;
    public static final byte TEXT = 0x01;
    public static final byte BINARY = 0x02;
    public static final byte CONNECTION_CLOSE = 0x08;
    public static final byte PING = 0x09;
    public static final byte PONG = 0x0A;
}
