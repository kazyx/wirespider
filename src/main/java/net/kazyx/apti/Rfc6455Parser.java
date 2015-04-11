package net.kazyx.apti;

class Rfc6455Parser {
    private static final int BYTE = 255;
    private static final int FIN = 128;
    private static final int MASK = 128;
    private static final int RSV1 = 64;
    private static final int RSV2 = 32;
    private static final int RSV3 = 16;
    private static final int OPCODE = 15;
    private static final int LENGTH = 127;

    static final int OP_CONTINUATION = 0;
    static final int OP_TEXT = 1;
    static final int OP_BINARY = 2;
    static final int OP_CLOSE = 8;
    static final int OP_PING = 9;
    static final int OP_PONG = 10;

    private final boolean mIsClient;

    Rfc6455Parser(boolean isClient) {
        mIsClient = isClient;
    }

    byte[] asFrame(String data) {
        return asFrame(data, OP_TEXT, -1);
    }

    byte[] asFrame(byte[] data) {
        return asFrame(data, OP_BINARY, -1);
    }

    byte[] asPingFrame() {
        return asFrame("ping", OP_PING, -1);
    }

    byte[] asPongFrame() {
        return asFrame("pong", OP_PONG, -1);
    }

    byte[] asCloseFrame(CloseStatusCode code, String reason) {
        return asFrame(reason, OP_CLOSE, code.statusCode);
    }

    byte[] asFrame(String data, int opcode, int errorCode) {
        return asFrame(ByteArrayUtil.fromText(data), opcode, errorCode);
    }

    private byte[] asFrame(byte[] data, int opcode, int errorCode) {
        byte[] buffer = data;
        int insert = (errorCode > 0) ? 2 : 0;
        int length = buffer.length + insert;
        int header = (length <= 125) ? 2 : (length <= 65535 ? 4 : 10);
        int offset = header + (mIsClient ? 4 : 0);
        int masked = mIsClient ? MASK : 0;
        byte[] frame = new byte[length + offset];

        frame[0] = (byte) ((byte) FIN | (byte) opcode);

        if (length <= 125) {
            frame[1] = (byte) (masked | length);
        } else if (length <= 65535) {
            frame[1] = (byte) (masked | 126);
            frame[2] = (byte) Math.floor(length / 256);
            frame[3] = (byte) (length & BYTE);
        } else {
            frame[1] = (byte) (masked | 127);
            frame[2] = (byte) (((int) Math.floor(length / Math.pow(2, 56))) & BYTE);
            frame[3] = (byte) (((int) Math.floor(length / Math.pow(2, 48))) & BYTE);
            frame[4] = (byte) (((int) Math.floor(length / Math.pow(2, 40))) & BYTE);
            frame[5] = (byte) (((int) Math.floor(length / Math.pow(2, 32))) & BYTE);
            frame[6] = (byte) (((int) Math.floor(length / Math.pow(2, 24))) & BYTE);
            frame[7] = (byte) (((int) Math.floor(length / Math.pow(2, 16))) & BYTE);
            frame[8] = (byte) (((int) Math.floor(length / Math.pow(2, 8))) & BYTE);
            frame[9] = (byte) (length & BYTE);
        }

        if (errorCode > 0) {
            frame[offset] = (byte) (((int) Math.floor(errorCode / 256)) & BYTE);
            frame[offset + 1] = (byte) (errorCode & BYTE);
        }
        System.arraycopy(buffer, 0, frame, offset + insert, buffer.length);

        if (mIsClient) {
            byte[] mask = {
                    (byte) Math.floor(Math.random() * 256), (byte) Math.floor(Math.random() * 256),
                    (byte) Math.floor(Math.random() * 256), (byte) Math.floor(Math.random() * 256)
            };
            System.arraycopy(mask, 0, frame, header, mask.length);
            mask(frame, mask, offset);
        }

        return frame;
    }

    static byte[] mask(byte[] payload, byte[] mask, int offset) {
        if (mask.length == 0) return payload;

        for (int i = 0; i < payload.length - offset; i++) {
            payload[offset + i] = (byte) (payload[offset + i] ^ mask[i % 4]);
        }
        return payload;
    }

    static int asOpcode(byte firstByte) throws ProtocolViolationException {
        boolean rsv1 = (firstByte & RSV1) == RSV1;
        boolean rsv2 = (firstByte & RSV2) == RSV2;
        boolean rsv3 = (firstByte & RSV3) == RSV3;

        if (rsv1 || rsv2 || rsv3) {
            throw new ProtocolViolationException("RSV non-zero");
        }

        return firstByte & OPCODE;
    }

    static boolean asIsFinal(byte firstByte) {
        return (firstByte & FIN) == FIN;
    }

    static boolean asIsMasked(byte secondByte) {
        return (secondByte & MASK) == MASK;
    }

    static int asPayloadLength(byte secondByte) throws ProtocolViolationException {
        int length = secondByte & LENGTH;
        if (length == 0) {
            throw new ProtocolViolationException("Payload length zero");
        }
        return length;
    }

    static int asExtendedPayloadLength(byte[] data) throws ProtocolViolationException {
        return ByteArrayUtil.toUnsignedInteger(data);
    }
}
