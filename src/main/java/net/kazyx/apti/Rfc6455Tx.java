package net.kazyx.apti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class Rfc6455Tx implements FrameTx {

    private final boolean mIsClient;
    private final SelectionHandler mHandler;
    private final WebSocket mWebSocket;

    private boolean mIsCloseSent = false;

    Rfc6455Tx(WebSocket websocket, boolean isClient, SelectionHandler socket) {
        mWebSocket = websocket;
        mIsClient = isClient;
        mHandler = socket;
    }

    @Override
    public void sendTextAsync(String data) {
        sendFrameAsync(OpCode.TEXT, ByteArrayUtil.fromText(data));
    }

    @Override
    public void sendBinaryAsync(byte[] data) {
        sendFrameAsync(OpCode.BINARY, data);
    }

    @Override
    public void sendPingAsync() {
        sendFrameAsync(OpCode.PING, ByteArrayUtil.fromText("ping"));
    }

    @Override
    public void sendPongAsync(String pingMessage) {
        sendFrameAsync(OpCode.PONG, ByteArrayUtil.fromText(pingMessage));
    }

    @Override
    public void sendCloseAsync(CloseStatusCode code, String reason) {
        byte[] messageBytes = ByteArrayUtil.fromText(reason);
        byte[] payload = new byte[2 + messageBytes.length];
        payload[0] = (byte) (code.statusCode >>> 8);
        payload[1] = (byte) (code.statusCode);
        System.arraycopy(messageBytes, 0, payload, 2, messageBytes.length);

        sendFrameAsync(OpCode.CONNECTION_CLOSE, payload);
    }

    private synchronized void sendFrameAsync(byte opcode, byte[] payload) {
        if (mIsCloseSent) {
            return;
        }
        if (opcode == OpCode.CONNECTION_CLOSE) {
            mIsCloseSent = true;
        }
        long payloadLength = payload.length; // Length of array is Integer.MAX_VALUE
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

            byte[] maskingKey = {
                    (byte) Math.floor(Math.random() * 256), (byte) Math.floor(Math.random() * 256),
                    (byte) Math.floor(Math.random() * 256), (byte) Math.floor(Math.random() * 256)
            };
            mStream.write(maskingKey);

            if (mIsClient) {
                mStream.write(BitMask.maskAll(payload, maskingKey));
            } else {
                mStream.write(payload);
            }

            mHandler.writeAsync(mStream.toByteArray());
        } catch (IOException e) {
            IOUtil.close(mStream);
            mWebSocket.onCloseFrame(CloseStatusCode.ABNORMAL_CLOSURE.statusCode, e.getMessage());
        }
    }
}
