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
import net.kazyx.wirespider.FrameRx;
import net.kazyx.wirespider.OpCode;
import net.kazyx.wirespider.exception.PayloadOverflowException;
import net.kazyx.wirespider.exception.PayloadUnderflowException;
import net.kazyx.wirespider.exception.ProtocolViolationException;
import net.kazyx.wirespider.extension.Extension;
import net.kazyx.wirespider.util.BinaryUtil;
import net.kazyx.wirespider.util.WsLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.zip.ZipException;

class Rfc6455Rx implements FrameRx {
    private static final String TAG = Rfc6455Rx.class.getSimpleName();

    private final FrameRx.Listener mListener;
    private final int mMaxPayloadSize;
    private List<Extension> mExtensions = Collections.emptyList();
    private final boolean mIsClient;

    Rfc6455Rx(FrameRx.Listener listener, int maxPayload, boolean isClient) {
        mListener = listener;
        mMaxPayloadSize = maxPayload;
        mIsClient = isClient;
    }

    @Override
    public void setExtensions(List<Extension> extensions) {
        mExtensions = extensions;
    }

    private boolean isFinal;
    private byte opcode;
    private byte first;

    private final Runnable mReadOpCodeOperation = new Runnable() {
        @Override
        public void run() {
            try {
                first = readBytes(1).array()[0];
                isFinal = BinaryUtil.isFlagMatched(first, (byte) 0x80);

                int maskedRsvBits = first & 0x70;
                for (Extension ext : mExtensions) {
                    maskedRsvBits = maskedRsvBits ^ ext.reservedBits();
                }
                if (maskedRsvBits != 0) {
                    throw new ProtocolViolationException("Reserved bits invalid");
                }

                opcode = (byte) (first & 0x0f);
                mSecondByteOperation.run();
            } catch (PayloadUnderflowException e) {
                // No need to flush this log. Always happens at frame end.
                // Log.d(TAG, "BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException e) {
                WsLog.d(TAG, "Protocol violation", e.getMessage());
                mListener.onProtocolViolation();
            }
        }
    };

    private boolean isMasked;
    private int payloadLength;

    private final Runnable mSecondByteOperation = new Runnable() {
        @Override
        public void run() {
            try {
                byte second = readBytes(1).array()[0];
                isMasked = BinaryUtil.isFlagMatched(second, (byte) 0x80);

                if (mIsClient == isMasked) {
                    throw new ProtocolViolationException("Masked payload from server or unmasked payload from client");
                }

                payloadLength = second & 0x7f;
                if (payloadLength > mMaxPayloadSize) {
                    throw new PayloadOverflowException("Payload size exceeds " + mMaxPayloadSize);
                }
                switch (payloadLength) {
                    case 126:
                    case 127:
                        mExtendedPayloadOperation.run();
                        break;
                    default:
                        if (isMasked) {
                            mMaskKeyOperation.run();
                        } else {
                            mPayloadOperation.run();
                        }
                        break;
                }
            } catch (PayloadUnderflowException e) {
                WsLog.v(TAG, "SecondByte BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (PayloadOverflowException e) {
                WsLog.d(TAG, "Payload size overflow", e.getMessage());
                mListener.onPayloadOverflow();
            } catch (ProtocolViolationException e) {
                WsLog.d(TAG, "Protocol violation", e.getMessage());
                mListener.onProtocolViolation();
            }
        }
    };

    private final Runnable mExtendedPayloadOperation = new Runnable() {
        @Override
        public void run() {
            int size = payloadLength == 126 ? 2 : 8;
            try {
                // TODO support large payload over 2GB
                payloadLength = BinaryUtil.toUnsignedInteger(readBytes(size));
                if (payloadLength > mMaxPayloadSize) {
                    throw new PayloadOverflowException("Payload size exceeds " + mMaxPayloadSize);
                }
                if (isMasked) {
                    mMaskKeyOperation.run();
                } else {
                    mPayloadOperation.run();
                }
            } catch (PayloadUnderflowException e) {
                WsLog.v(TAG, "ExtendedPayloadLength BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (PayloadOverflowException | IllegalArgumentException e) {
                WsLog.d(TAG, "Payload size overflow", e.getMessage());
                mListener.onPayloadOverflow();
            }
        }
    };

    private byte[] mask;

    private final Runnable mMaskKeyOperation = new Runnable() {
        @Override
        public void run() {
            try {
                mask = readBytes(4).array();
                mPayloadOperation.run();
            } catch (PayloadUnderflowException e) {
                WsLog.v(TAG, "MaskKey BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            }
        }
    };

    private final Runnable mPayloadOperation = new Runnable() {
        @Override
        public void run() {
            try {
                ByteBuffer payload = readBytes(payloadLength);
                if (isMasked) {
                    BinaryUtil.maskAll(payload, mask);
                }

                handleFrame(opcode, payload, isFinal);
                mReadOpCodeOperation.run();
            } catch (PayloadUnderflowException e) {
                if (mSuspendedOperation != this) {
                    // Flush log only for the first time
                    WsLog.v(TAG, "Payload BufferUnsatisfied");
                }
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException | IllegalArgumentException e) {
                WsLog.d(TAG, "Protocol violation", e.getMessage());
                mListener.onProtocolViolation();
            } catch (ZipException e) {
                mListener.onCloseFrame(CloseStatusCode.ABNORMAL_CLOSURE.asNumber(), "Invalid compressed data");
            } catch (IOException e) {
                // Never happens.
                mListener.onCloseFrame(CloseStatusCode.ABNORMAL_CLOSURE.asNumber(), "Unexpected IOException");
            }
        }
    };

    private enum ContinuationMode {
        TEXT,
        BINARY,
        UNSET,
    }

    private ContinuationMode mContinuation = ContinuationMode.UNSET;
    private final ByteArrayOutputStream mContinuationBuffer = new ByteArrayOutputStream();

    private void handleFrame(byte opcode, ByteBuffer payload, boolean isFinal) throws ProtocolViolationException, IOException {
        // WsLog.v(TAG, "handleFrame", opcode);
        switch (opcode) {
            case OpCode.CONTINUATION: {
                if (mContinuation == ContinuationMode.UNSET) {
                    throw new ProtocolViolationException("Sudden continuation opcode");
                }
                int length = payload.remaining();
                mContinuationBuffer.write(BinaryUtil.toBytesRemaining(payload), 0, length);
                if (isFinal) {
                    ByteBuffer binary = ByteBuffer.wrap(mContinuationBuffer.toByteArray());
                    mContinuationBuffer.reset();
                    if (mContinuation == ContinuationMode.BINARY) {
                        handleBinaryFrame(binary);
                    } else {
                        handleTextFrame(binary);
                    }
                    mContinuation = ContinuationMode.UNSET;
                }
                break;
            }
            case OpCode.TEXT: {
                if (isFinal) {
                    handleTextFrame(payload);
                } else {
                    int length = payload.remaining();
                    mContinuationBuffer.write(BinaryUtil.toBytesRemaining(payload), 0, length);
                    mContinuation = ContinuationMode.TEXT;
                }
                break;
            }
            case OpCode.BINARY: {
                if (isFinal) {
                    handleBinaryFrame(payload);
                } else {
                    int length = payload.remaining();
                    mContinuationBuffer.write(BinaryUtil.toBytesRemaining(payload), 0, length);
                    mContinuation = ContinuationMode.BINARY;
                }
                break;
            }
            case OpCode.PING:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for ping opcode");
                }
                if (payload.remaining() > 125) {
                    throw new ProtocolViolationException("Ping payload too large");
                }
                mListener.onPingFrame(BinaryUtil.toTextAll(payload));
                break;
            case OpCode.PONG:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for pong opcode");
                }
                mListener.onPongFrame(BinaryUtil.toTextAll(payload));
                break;
            case OpCode.CONNECTION_CLOSE:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for close opcode");
                }
                int code = (payload.remaining() >= 2) ? (payload.get() << 8) + (payload.get() & 0xFF) : CloseStatusCode.NO_STATUS_RECEIVED.asNumber();
                String reason = (payload.remaining() > 2) ? BinaryUtil.toTextRemaining(payload) : "";
                mListener.onCloseFrame(code, reason);
                break;
            default:
                throw new ProtocolViolationException("Bad opcode: " + opcode);
        }
    }

    private void handleBinaryFrame(ByteBuffer buffer) throws IOException {
        for (Extension ext : mExtensions) {
            if (BinaryUtil.isFlagMatched(first, ext.reservedBits())) {
                buffer = ext.filter().onReceivingBinary(buffer);
            }
        }
        mListener.onBinaryMessage(buffer);
    }

    private void handleTextFrame(ByteBuffer buffer) throws IOException {
        for (Extension ext : mExtensions) {
            if (BinaryUtil.isFlagMatched(first, ext.reservedBits())) {
                buffer = ext.filter().onReceivingText(buffer);
            }
        }
        String text = BinaryUtil.toTextAll(buffer);
        mListener.onTextMessage(text);
    }

    @Override
    public void onDataReceived(ByteBuffer data) {
        // Log.d(TAG, "onDataReceived");
        mReceivedBuffer.addLast(data);
        mBufferSize += data.remaining();

        synchronized (mOperationSequenceLock) {
            if (mWaitingSize <= mBufferSize) {
                mSuspendedOperation.run();
            }
        }
    }

    private final Object mOperationSequenceLock = new Object();

    private Runnable mSuspendedOperation = mReadOpCodeOperation;

    private int mWaitingSize = 0;

    private int mBufferSize = 0;

    private final Deque<ByteBuffer> mReceivedBuffer = new ArrayDeque<>();

    private ByteBuffer readBytes(int length) throws PayloadUnderflowException {
        if (mBufferSize < length) {
            mWaitingSize = length;
            throw new PayloadUnderflowException();
        }

        ByteBuffer ret = ByteBuffer.allocate(length);

        while (!mReceivedBuffer.isEmpty()) {
            ByteBuffer buff = mReceivedBuffer.getFirst();
            int copied = Math.min(ret.remaining(), buff.remaining());
            if (ret.remaining() < buff.remaining()) {
                byte[] tmp = new byte[copied];
                buff.get(tmp);
                ret.put(tmp);
            } else {
                ret.put(buff);
            }

            if (buff.remaining() == 0) {
                mReceivedBuffer.remove();
            }

            if (ret.remaining() == 0) {
                break;
            }
        }

        mBufferSize -= length;
        ret.flip();
        return ret;
    }
}
