package net.kazyx.apti;

public enum CloseStatusCode {
    NORMAL_CLOSURE(1000),
    GOING_AWAY(1001),
    PROTOCOL_ERROR(1002),
    UNSUPPORTED_DATA(1003),
    RESERVED(1004),
    NO_STATUS_RECEIVED(1005),
    ABNORMAL_CLOSURE(1006),
    INVALID_FRAME_PAYLOAD_DATA(1007),
    POLICY_VIOLATION(1008),
    MESSAGE_TOO_BIG(1009),
    MANDATORY_EXTENSION(1010),
    INTERNAL_SERVER_ERROR(1011),
    TLS_HANDSHAKE(1015);

    final int statusCode;

    CloseStatusCode(int code) {
        this.statusCode = code;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
