package net.kazyx.apti;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

class Rfc6455Tx implements FrameTx {

    private final boolean mIsClient;
    private final OutputStream mStream;
    private final WebSocket mWebSocket;

    private boolean mIsCloseSent = false;

    Rfc6455Tx(WebSocket websocket, boolean isClient, Socket socket) throws IOException {
        mWebSocket = websocket;
        mIsClient = isClient;
        mStream = new BufferedOutputStream(socket.getOutputStream());
    }

    @Override
    public void sendTextFrame(String data) {
        sendFrame(OpCode.TEXT, ByteArrayUtil.fromText(data));
    }

    @Override
    public void sendBinaryFrame(byte[] data) {
        sendFrame(OpCode.BINARY, data);
    }

    @Override
    public void sendPingFrame() {
        sendFrame(OpCode.PING, ByteArrayUtil.fromText("ping"));
    }

    @Override
    public void sendPongFrame(String pingMessage) {
        sendFrame(OpCode.PONG, ByteArrayUtil.fromText(pingMessage));
    }

    @Override
    public void sendCloseFrame(CloseStatusCode code, String reason) {
        byte[] messageBytes = ByteArrayUtil.fromText(reason);
        byte[] payload = new byte[2 + messageBytes.length];
        payload[0] = (byte) (code.statusCode >>> 8);
        payload[1] = (byte) (code.statusCode);
        System.arraycopy(messageBytes, 0, payload, 2, messageBytes.length);

        sendFrame(OpCode.CONNECTION_CLOSE, payload);
    }

    private synchronized void sendFrame(byte opcode, byte[] payload) {
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

            mStream.flush();
        } catch (IOException e) {
            IOUtil.close(mStream);
            mWebSocket.onCloseFrame(CloseStatusCode.ABNORMAL_CLOSURE.statusCode, e.getMessage());
        }
    }
}
