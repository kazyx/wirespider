package net.kazyx.apti;

final class OpCode {
    private OpCode() {
    }

    static final int CONTINUATION = 0;
    static final int TEXT = 1;
    static final int BINARY = 2;
    static final int CONNECTION_CLOSE = 8;
    static final int PING = 9;
    static final int PONG = 10;
}
