package net.kazyx.apti;

class Rfc6455FrameGenerator implements FrameGenerator {

    private final boolean mIsClient;

    Rfc6455FrameGenerator(boolean isClient) {
        mIsClient = isClient;
    }

    @Override
    public byte[] createTextFrame(String data) {
        return createFrame(data, OpCode.TEXT, -1);
    }

    @Override
    public byte[] createBinaryFrame(byte[] data) {
        return createFrame(data, OpCode.BINARY, -1);
    }

    @Override
    public byte[] createPingFrame() {
        return createFrame("ping", OpCode.PING, -1);
    }

    @Override
    public byte[] createPongFrame(String pingMessage) {
        return createFrame(pingMessage, OpCode.PONG, -1);
    }

    @Override
    public byte[] createCloseFrame(CloseStatusCode code, String reason) {
        return createFrame(reason, OpCode.CONNECTION_CLOSE, code.getStatusCode());
    }

    byte[] createFrame(String data, int opcode, int errorCode) {
        return createFrame(ByteArrayUtil.fromText(data), opcode, errorCode);
    }

    private byte[] createFrame(byte[] data, int opcode, int errorCode) {
        int insert = (errorCode > 0) ? 2 : 0;
        int length = data.length + insert;
        int header = (length <= 125) ? 2 : (length <= 65535 ? 4 : 10);
        int offset = header + (mIsClient ? 4 : 0);
        int masked = mIsClient ? Rfc6455.BIT_MASK_MASK : 0;
        byte[] frame = new byte[length + offset];

        frame[0] = (byte) ((byte) Rfc6455.BIT_MASK_FIN | (byte) opcode);

        if (length <= 125) {
            frame[1] = (byte) (masked | length);
        } else if (length <= 65535) {
            frame[1] = (byte) (masked | 126);
            frame[2] = (byte) Math.floor(length / 256);
            frame[3] = (byte) (length & Rfc6455.BIT_MASK_BYTE);
        } else {
            frame[1] = (byte) (masked | 127);
            frame[2] = (byte) (((int) Math.floor(length / Math.pow(2, 56))) & Rfc6455.BIT_MASK_BYTE);
            frame[3] = (byte) (((int) Math.floor(length / Math.pow(2, 48))) & Rfc6455.BIT_MASK_BYTE);
            frame[4] = (byte) (((int) Math.floor(length / Math.pow(2, 40))) & Rfc6455.BIT_MASK_BYTE);
            frame[5] = (byte) (((int) Math.floor(length / Math.pow(2, 32))) & Rfc6455.BIT_MASK_BYTE);
            frame[6] = (byte) (((int) Math.floor(length / Math.pow(2, 24))) & Rfc6455.BIT_MASK_BYTE);
            frame[7] = (byte) (((int) Math.floor(length / Math.pow(2, 16))) & Rfc6455.BIT_MASK_BYTE);
            frame[8] = (byte) (((int) Math.floor(length / Math.pow(2, 8))) & Rfc6455.BIT_MASK_BYTE);
            frame[9] = (byte) (length & Rfc6455.BIT_MASK_BYTE);
        }

        if (errorCode > 0) {
            frame[offset] = (byte) (((int) Math.floor(errorCode / 256)) & Rfc6455.BIT_MASK_BYTE);
            frame[offset + 1] = (byte) (errorCode & Rfc6455.BIT_MASK_BYTE);
        }
        System.arraycopy(data, 0, frame, offset + insert, data.length);

        if (mIsClient) {
            byte[] mask = {
                    (byte) Math.floor(Math.random() * 256), (byte) Math.floor(Math.random() * 256),
                    (byte) Math.floor(Math.random() * 256), (byte) Math.floor(Math.random() * 256)
            };
            System.arraycopy(mask, 0, frame, header, mask.length);
            BitMask.mask(frame, mask, offset);
        }

        return frame;
    }
}
