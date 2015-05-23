package net.kazyx.apti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class Rfc6455Tx implements FrameTx {
    private static final String TAG = Rfc6455Tx.class.getSimpleName();

    private final boolean mIsClient;
    private final SocketChannelWriter mWriter;

    private final Object mCloseFlagLock = new Object();
    private boolean mIsCloseSent = false;

    Rfc6455Tx(SocketChannelWriter writer, boolean isClient) {
        mIsClient = isClient;
        mWriter = writer;
    }

    @Override
    public void sendTextAsync(String data) {
        Log.v(TAG, "sendTextAsync", data);
        sendFrameAsync(OpCode.TEXT, ByteArrayUtil.fromText(data));
    }

    @Override
    public void sendBinaryAsync(byte[] data) {
        Log.v(TAG, "sendBinaryAsync", data.length);
        sendFrameAsync(OpCode.BINARY, data);
    }

    @Override
    public void sendPingAsync() {
        Log.v(TAG, "sendPingAsync");
        sendFrameAsync(OpCode.PING, ByteArrayUtil.fromText("ping"));
    }

    @Override
    public void sendPongAsync(String pingMessage) {
        Log.v(TAG, "sendPongAsync", pingMessage);
        sendFrameAsync(OpCode.PONG, ByteArrayUtil.fromText(pingMessage));
    }

    @Override
    public void sendCloseAsync(CloseStatusCode code, String reason) {
        Log.v(TAG, "sendCloseAsync");
        byte[] messageBytes = ByteArrayUtil.fromText(reason);
        byte[] payload = new byte[2 + messageBytes.length];
        payload[0] = (byte) (code.statusCode >>> 8);
        payload[1] = (byte) (code.statusCode);
        System.arraycopy(messageBytes, 0, payload, 2, messageBytes.length);

        sendFrameAsync(OpCode.CONNECTION_CLOSE, payload);
    }

    private void sendFrameAsync(byte opcode, byte[] payload) {
        synchronized (mCloseFlagLock) {
            if (mIsCloseSent) {
                return;
            }
            if (opcode == OpCode.CONNECTION_CLOSE) {
                mIsCloseSent = true;
            }
        }

        long payloadLength = payload.length; // Maximum length of array is Integer.MAX_VALUE
        int headerLength = (payloadLength <= 125) ? 2 : (payloadLength <= 65535 ? 4 : 10);
        byte[] header = new byte[headerLength];

        header[0] = (byte) (BitMask.BYTE_SYM_0x80 | opcode);

        if (headerLength == 2) {
            if (mIsClient) {
                header[1] = (byte) (BitMask.BYTE_SYM_0x80 | payloadLength);
            } else {
                header[1] = (byte) (payloadLength);
            }
        } else if (headerLength == 4) {
            if (mIsClient) {
                header[1] = BitMask.BYTE_SYM_0xFE;
            } else {
                header[1] = BitMask.BYTE_SYM_0x7E;
            }
            header[2] = (byte) (payloadLength >>> 8);
            header[3] = (byte) (payloadLength);
        } else {
            if (mIsClient) {
                header[1] = BitMask.BYTE_SYM_0xFF;
            } else {
                header[1] = BitMask.BYTE_SYM_0x7F;
            }
            header[2] = (byte) (payloadLength >>> 56);
            header[3] = (byte) (payloadLength >>> 48);
            header[4] = (byte) (payloadLength >>> 40);
            header[5] = (byte) (payloadLength >>> 32);
            header[6] = (byte) (payloadLength >>> 24);
            header[7] = (byte) (payloadLength >>> 16);
            header[8] = (byte) (payloadLength >>> 8);
            header[9] = (byte) (payloadLength);
        }

        ByteArrayOutputStream mStream = new ByteArrayOutputStream();
        try {
            mStream.write(header);

            if (mIsClient) {
                int mask = RandomSource.random().nextInt();
                byte[] maskingKey = {
                        (byte) mask,
                        (byte) (mask >>> 8),
                        (byte) (mask >>> 16),
                        (byte) (mask >>> 24)
                };
                mStream.write(maskingKey);
                mStream.write(BitMask.maskAll(payload, maskingKey));
            } else {
                mStream.write(payload);
            }

            mWriter.writeAsync(mStream.toByteArray());
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException
            throw new IllegalStateException(e);
        } finally {
            IOUtil.close(mStream);
        }
    }
}
