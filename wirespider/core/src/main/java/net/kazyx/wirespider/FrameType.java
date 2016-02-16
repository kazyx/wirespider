/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

public enum FrameType {
    CONTINUATION(OpCode.CONTINUATION),
    TEXT(OpCode.TEXT),
    BINARY(OpCode.BINARY),
    CLOSE(OpCode.CONNECTION_CLOSE),
    PING(OpCode.PING),
    PONG(OpCode.PONG);

    private final byte opcode;

    FrameType(byte opcode) {
        this.opcode = opcode;
    }

    public byte opcode() {
        return opcode;
    }
}
