/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.rfc6455;

import net.kazyx.wirespider.CloseStatusCode;
import net.kazyx.wirespider.FrameTx;
import net.kazyx.wirespider.OpCode;
import net.kazyx.wirespider.SocketChannelWriter;
import net.kazyx.wirespider.extension.Extension;
import net.kazyx.wirespider.util.BitMask;
import net.kazyx.wirespider.util.ByteArrayUtil;
import net.kazyx.wirespider.util.WsLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

class Rfc6455Tx implements FrameTx {
    private static final String TAG = Rfc6455Tx.class.getSimpleName();

    private static final int MAX_CLIENT_HEADER_LENGTH = 14; // Max server header length is 10

    private List<Extension> mExtensions = Collections.emptyList();
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
        byte extensionBits = 0;
        for (Extension ext : mExtensions) {
            try {
                buff = ext.filter().onSendingText(buff);
                extensionBits = (byte) (extensionBits | ext.reservedBits());
            } catch (IOException e) {
                // Filtering error. Send original data.
                WsLog.v(TAG, e.getMessage());
            }
        }

        sendFrameAsync(OpCode.TEXT, buff, extensionBits);
    }

    @Override
    public void sendBinaryAsync(byte[] data) {
        // WsLog.v(TAG, "sendBinaryAsync");
        ByteBuffer buff = ByteBuffer.wrap(data);
        byte extensionBits = 0;
        for (Extension ext : mExtensions) {
            try {
                buff = ext.filter().onSendingBinary(buff);
                extensionBits = (byte) (extensionBits | ext.reservedBits());
            } catch (IOException e) {
                // Filtering error. Send original data.
                WsLog.v(TAG, e.getMessage());
            }
        }

        sendFrameAsync(OpCode.BINARY, buff, extensionBits);
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
        payload.put((byte) (code.asNumber() >>> 8));
        payload.put((byte) (code.asNumber()));
        payload.put(messageBytes);
        payload.flip();

        sendFrameAsync(OpCode.CONNECTION_CLOSE, payload);
    }

    @Override
    public void setExtensions(List<Extension> extensions) {
        mExtensions = extensions;
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
            int mask = ThreadLocalRandom.current().nextInt();
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
