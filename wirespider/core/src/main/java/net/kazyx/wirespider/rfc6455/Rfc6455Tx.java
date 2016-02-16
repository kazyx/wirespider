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
import net.kazyx.wirespider.util.BinaryUtil;
import net.kazyx.wirespider.util.WsLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

class Rfc6455Tx implements FrameTx {
    private static final String TAG = Rfc6455Tx.class.getSimpleName();

    private static final int MAX_CLIENT_HEADER_LENGTH = 14; // Max server header length is 10

    private List<Extension> mExtensions = Collections.emptyList();
    private final boolean mIsClient;
    private final SocketChannelWriter mWriter;

    private final Object mCloseFlagLock = new Object();
    private boolean mIsCloseSent = false;

    private ReentrantLock mDataLock = new ReentrantLock();

    Rfc6455Tx(SocketChannelWriter writer, boolean isClient) {
        mIsClient = isClient;
        mWriter = writer;
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void sendTextAsync(String data) {
        // WsLog.v(TAG, "sendTextAsync");
        if (mDataLock.isLocked()) {
            throw new IllegalStateException("PartialMessageWriter is holding a lock");
        }
        sendTextFrame(data, OpCode.TEXT, true);
    }

    @Override
    public void sendTextAsyncPrivileged(String data, boolean continuation, boolean isFinal) {
        sendTextFrame(data, continuation ? OpCode.CONTINUATION : OpCode.TEXT, isFinal);
    }

    private void sendTextFrame(String data, byte opcode, boolean isFinal) {
        ByteBuffer buff = ByteBuffer.wrap(BinaryUtil.fromText(data));
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

        sendFrameAsync(opcode, buff, extensionBits, isFinal);
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void sendBinaryAsync(byte[] data) {
        // WsLog.v(TAG, "sendBinaryAsync");
        if (mDataLock.isLocked()) {
            throw new IllegalStateException("PartialMessageWriter is holding a lock");
        }
        sendBinaryFrame(data, OpCode.BINARY, true);
    }

    @Override
    public void sendBinaryAsyncPrivileged(byte[] data, boolean continuation, boolean isFinal) {
        sendBinaryFrame(data, continuation ? OpCode.CONTINUATION : OpCode.BINARY, isFinal);
    }

    private void sendBinaryFrame(byte[] data, byte opcode, boolean isFinal) {
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

        sendFrameAsync(opcode, buff, extensionBits, isFinal);
    }

    @Override
    public void sendPingAsync(String message) {
        // WsLog.v(TAG, "sendPingAsync");
        sendFrameAsync(OpCode.PING, ByteBuffer.wrap(BinaryUtil.fromText(message)), (byte) 0, true);
    }

    @Override
    public void sendPongAsync(String pingMessage) {
        // WsLog.v(TAG, "sendPongAsync", pingMessage);
        sendFrameAsync(OpCode.PONG, ByteBuffer.wrap(BinaryUtil.fromText(pingMessage)), (byte) 0, true);
    }

    @Override
    public void sendCloseAsync(CloseStatusCode code, String reason) {
        // WsLog.v(TAG, "sendCloseAsync");
        byte[] messageBytes = BinaryUtil.fromText(reason);
        ByteBuffer payload = ByteBuffer.allocate(2 + messageBytes.length);
        payload.put((byte) (code.asNumber() >>> 8));
        payload.put((byte) (code.asNumber()));
        payload.put(messageBytes);
        payload.flip();

        sendFrameAsync(OpCode.CONNECTION_CLOSE, payload, (byte) 0, true);
    }

    @Override
    public void setExtensions(List<Extension> extensions) {
        mExtensions = extensions;
    }

    /**
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void lock() {
        if (mDataLock.isLocked()) {
            throw new IllegalStateException("Another PartialMessageWriter is holding a lock");
        }
        mDataLock.lock();
    }

    /**
     * @throws IllegalMonitorStateException {@inheritDoc}
     */
    @Override
    public void unlock() {
        mDataLock.unlock();
    }

    private void sendFrameAsync(byte opcode, ByteBuffer payload, byte extensionFlags, boolean isFinal) {
        WsLog.v(TAG, "SendFrameAsync: " + opcode + " - " + isFinal);
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

        byte firstBase = isFinal ? (byte) (0x80) : 0;
        header[0] = (byte) (firstBase | opcode | extensionFlags);

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
            BinaryUtil.maskAll(payload, maskingKey);
        }

        buffer.put(payload);
        buffer.flip();

        mWriter.writeAsync(buffer);
    }
}
