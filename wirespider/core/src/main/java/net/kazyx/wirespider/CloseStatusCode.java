/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

/**
 * WebSocket close status code.
 */
public enum CloseStatusCode {
    /**
     * 1000: Normal closure.
     */
    NORMAL_CLOSURE(1000),
    /**
     * 1001: Going away.
     */
    GOING_AWAY(1001),
    /**
     * 1002: Protocol error.
     */
    PROTOCOL_ERROR(1002),
    /**
     * 1003: Unsupported data.
     */
    UNSUPPORTED_DATA(1003),
    /**
     * 1004: Reserved.
     */
    RESERVED(1004),
    /**
     * 1005: Fallback value.
     */
    NO_STATUS_RECEIVED(1005),
    /**
     * 1006: Abnormal closure.
     */
    ABNORMAL_CLOSURE(1006),
    /**
     * 1007: Invalid frame payload data.
     */
    INVALID_FRAME_PAYLOAD_DATA(1007),
    /**
     * 1008: Policy violation.
     */
    POLICY_VIOLATION(1008),
    /**
     * 1009 Message too big.
     */
    MESSAGE_TOO_BIG(1009),
    /**
     * 1010: Mandatory extension.
     */
    MANDATORY_EXTENSION(1010),
    /**
     * 1011: Internal server error.
     */
    INTERNAL_SERVER_ERROR(1011),
    /**
     * 1015: TLS handshake.
     */
    TLS_HANDSHAKE(1015);

    final int statusCode;

    CloseStatusCode(int code) {
        this.statusCode = code;
    }

    /**
     * @return Status code in number expression.
     */
    public int asNumber() {
        return statusCode;
    }
}
