package net.kazyx.apti;

enum CloseStatusCode {
    NORMAL_CLOSURE(1000),
    GOING_AWAY(1001),
    PROTOCOL_ERROR(1002),
    UNACCEPTABLE_DATA(1003),
    INVALID_TYPE_MESSAGE(1007),
    GENERAL(1008),
    MESSAGE_TOO_LARGE(1009),
    EXTENSION_LACKED(1010),
    UNEXPECTED_CONDITION(1011);

    final int statusCode;

    CloseStatusCode(int code) {
        this.statusCode = code;
    }
}
