package net.kazyx.apti;

final class Rfc6455 {
    private Rfc6455() {
    }

    static final int BIT_MASK_BYTE = 255;
    static final int BIT_MASK_FIN = 128;
    static final int BIT_MASK_MASK = 128;
    static final int BIT_MASK_RSV = 64 + 32 + 16;
    static final int BIT_MASK_OPCODE = 15;
    static final int BIT_MASK_PAYLOAD_LENGTH = 127;
}
