/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.PayloadFilter;
import net.kazyx.wirespider.util.BitMask;
import net.kazyx.wirespider.util.ByteArrayUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

class Rfc6455Tx implements FrameTx {
    private static final String TAG = Rfc6455Tx.class.getSimpleName();

    private static final int MAX_CLIENT_HEADER_LENGTH = 14; // Max server header length is 10

    private PayloadFilter mFilter;
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
        // WsLog.v(TAG, "sendTextAsync");
        ByteBuffer buff = ByteBuffer.wrap(ByteArrayUtil.fromText(data));
        if (mFilter != null) {
            byte[] flags = new byte[]{(byte) 0x00};
            try {
                buff = mFilter.onSendingText(buff, flags);
                sendFrameAsync(OpCode.TEXT, buff, flags[0]);
                return;
            } catch (IOException e) {
                // Filtering error. Send original data.
                WsLog.printStackTrace(TAG, e);
            }
        }
        sendFrameAsync(OpCode.TEXT, buff);
    }

    @Override
    public void sendBinaryAsync(byte[] data) {
        // WsLog.v(TAG, "sendBinaryAsync");
        ByteBuffer buff = ByteBuffer.wrap(data);
        if (mFilter != null) {
            byte[] flags = new byte[]{(byte) 0x00};
            try {
                buff = mFilter.onSendingBinary(buff, flags);
                sendFrameAsync(OpCode.BINARY, buff, flags[0]);
                return;
            } catch (IOException e) {
                // Filtering error. Send original data.
                WsLog.printStackTrace(TAG, e);
            }
        }
        sendFrameAsync(OpCode.BINARY, buff);
    }

    @Override
    public void sendPingAsync(String message) {
        // WsLog.v(TAG, "sendPingAsync");
        sendFrameAsync(OpCode.PING, ByteBuffer.wrap(ByteArrayUtil.fromText(message)));
    }

    @Override
    public void sendPongAsync(String pingMessage) {
        // WsLog.v(TAG, "sendPongAsync", pingMessage);
        sendFrameAsync(OpCode.PONG, ByteBuffer.wrap(ByteArrayUtil.fromText(pingMessage)));
    }

    @Override
    public void sendCloseAsync(CloseStatusCode code, String reason) {
        // WsLog.v(TAG, "sendCloseAsync");
        byte[] messageBytes = ByteArrayUtil.fromText(reason);
        ByteBuffer payload = ByteBuffer.allocate(2 + messageBytes.length);
        payload.put((byte) (code.statusCode >>> 8));
        payload.put((byte) (code.statusCode));
        payload.put(messageBytes);
        payload.flip();

        sendFrameAsync(OpCode.CONNECTION_CLOSE, payload);
    }

    @Override
    public void setPayloadFilter(PayloadFilter compression) {
        mFilter = compression;
    }

    private void sendFrameAsync(byte opcode, ByteBuffer payload) {
        sendFrameAsync(opcode, payload, (byte) 0);
    }

    private void sendFrameAsync(byte opcode, ByteBuffer payload, byte extensionFlags) {
        synchronized (mCloseFlagLock) {
            if (mIsCloseSent) {
                return;
            }
            if (opcode == OpCode.CONNECTION_CLOSE) {
                mIsCloseSent = true;
            }
        }

        long payloadLength = payload.remaining(); // Maximum length of array is Integer.MAX_VALUE
        int headerLength = (payloadLength <= 125) ? 2 : (payloadLength <= 65535 ? 4 : 10);
        byte[] header = new byte[headerLength];

        header[0] = (byte) (0x80 | opcode | extensionFlags);

        if (headerLength == 2) {
            if (mIsClient) {
                header[1] = (byte) (0x80 | payloadLength);
            } else {
                header[1] = (byte) (payloadLength);
            }
        } else if (headerLength == 4) {
            if (mIsClient) {
                header[1] = (byte) 0xfe;
            } else {
                header[1] = 0x7e;
            }
            header[2] = (byte) (payloadLength >>> 8);
            header[3] = (byte) (payloadLength);
        } else {
            if (mIsClient) {
                header[1] = (byte) 0xff;
            } else {
                header[1] = (byte) 0x7f;
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

        ByteBuffer buffer = ByteBuffer.allocate(MAX_CLIENT_HEADER_LENGTH + payload.remaining());
        buffer.put(header);

        if (mIsClient) {
            int mask = RandomSource.random().nextInt();
            byte[] maskingKey = {
                    (byte) mask,
                    (byte) (mask >>> 8),
                    (byte) (mask >>> 16),
                    (byte) (mask >>> 24)
            };
            buffer.put(maskingKey);
            BitMask.maskAll(payload, maskingKey);
        }

        buffer.put(payload);
        buffer.flip();

        mWriter.writeAsync(buffer);
    }
}
