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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.zip.ZipException;

class Rfc6455Rx implements FrameRx {
    private static final String TAG = Rfc6455Rx.class.getSimpleName();

    private final FrameRx.Listener mListener;
    private final int mMaxPayloadSize;
    private PayloadFilter mFilter;
    private final boolean mIsClient;

    Rfc6455Rx(FrameRx.Listener listener, int maxPayload, boolean isClient) {
        mListener = listener;
        mMaxPayloadSize = maxPayload;
        mIsClient = isClient;
    }

    @Override
    public void setPayloadFilter(PayloadFilter compression) {
        mFilter = compression;
    }

    private boolean isFinal;
    private byte opcode;
    private byte first;

    private final Runnable mReadOpCodeOperation = new Runnable() {
        @Override
        public void run() {
            try {
                first = readBytes(1).array()[0];
                isFinal = BitMask.isFlagMatched(first, (byte) 0x80);

                if (mFilter == null && (first & 0x70) != 0) {
                    throw new ProtocolViolationException("Reserved bits invalid");
                }
                opcode = (byte) (first & 0x0f);
                mSecondByteOperation.run();
            } catch (BufferUnsatisfiedException e) {
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
                isMasked = BitMask.isFlagMatched(second, (byte) 0x80);

                if (mIsClient == isMasked) {
                    throw new ProtocolViolationException("Masked payload from server or unmasked payload from client");
                }

                payloadLength = second & 0x7f;
                if (payloadLength > mMaxPayloadSize) {
                    throw new PayloadSizeOverflowException("Payload size exceeds " + mMaxPayloadSize);
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
            } catch (BufferUnsatisfiedException e) {
                WsLog.v(TAG, "SecondByte BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (PayloadSizeOverflowException e) {
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
                payloadLength = ByteArrayUtil.toUnsignedInteger(readBytes(size));
                if (payloadLength > mMaxPayloadSize) {
                    throw new PayloadSizeOverflowException("Payload size exceeds " + mMaxPayloadSize);
                }
                if (isMasked) {
                    mMaskKeyOperation.run();
                } else {
                    mPayloadOperation.run();
                }
            } catch (BufferUnsatisfiedException e) {
                WsLog.v(TAG, "ExtendedPayloadLength BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (PayloadSizeOverflowException | IllegalArgumentException e) {
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
            } catch (BufferUnsatisfiedException e) {
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
                    BitMask.maskAll(payload, mask);
                }

                handleFrame(opcode, payload, isFinal);
                mReadOpCodeOperation.run();
            } catch (BufferUnsatisfiedException e) {
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
                mContinuationBuffer.write(ByteArrayUtil.toBytesRemaining(payload), 0, length);
                if (isFinal) {
                    ByteBuffer binary = ByteBuffer.wrap(mContinuationBuffer.toByteArray());
                    mContinuationBuffer.reset();
                    if (mContinuation == ContinuationMode.BINARY) {
                        if (mFilter != null) {
                            binary = mFilter.onReceivingBinary(binary, first);
                        }
                        mListener.onBinaryMessage(binary);
                    } else {
                        if (mFilter != null) {
                            binary = mFilter.onReceivingText(binary, first);
                        }
                        mListener.onTextMessage(ByteArrayUtil.toTextAll(binary));
                    }
                    mContinuation = ContinuationMode.UNSET;
                }
                break;
            }
            case OpCode.TEXT: {
                if (isFinal) {
                    if (mFilter != null) {
                        payload = mFilter.onReceivingText(payload, first);
                    }
                    String text = ByteArrayUtil.toTextAll(payload);
                    mListener.onTextMessage(text);
                } else {
                    int length = payload.remaining();
                    mContinuationBuffer.write(ByteArrayUtil.toBytesRemaining(payload), 0, length);
                    mContinuation = ContinuationMode.TEXT;
                }
                break;
            }
            case OpCode.BINARY: {
                if (isFinal) {
                    if (mFilter != null) {
                        payload = mFilter.onReceivingBinary(payload, first);
                    }
                    mListener.onBinaryMessage(payload);
                } else {
                    int length = payload.remaining();
                    mContinuationBuffer.write(ByteArrayUtil.toBytesRemaining(payload), 0, length);
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
                mListener.onPingFrame(ByteArrayUtil.toTextAll(payload));
                break;
            case OpCode.PONG:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for pong opcode");
                }
                mListener.onPongFrame(ByteArrayUtil.toTextAll(payload));
                break;
            case OpCode.CONNECTION_CLOSE:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for close opcode");
                }
                int code = (payload.remaining() >= 2) ? (payload.get() << 8) + (payload.get() & 0xFF) : CloseStatusCode.NO_STATUS_RECEIVED.statusCode;
                String reason = (payload.remaining() > 2) ? ByteArrayUtil.toTextRemaining(payload) : "";
                mListener.onCloseFrame(code, reason);
                break;
            default:
                throw new ProtocolViolationException("Bad opcode: " + opcode);
        }
    }

    @Override
    public void onDataReceived(ByteBuffer data) {
        // Log.d(TAG, "onDataReceived");
        mReceivedBuffer.add(data);
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

    private final LinkedList<ByteBuffer> mReceivedBuffer = new LinkedList<>();

    private ByteBuffer readBytes(int length) throws BufferUnsatisfiedException {
        if (mBufferSize < length) {
            mWaitingSize = length;
            throw new BufferUnsatisfiedException();
        }

        ByteBuffer ret = ByteBuffer.allocate(length);

        ListIterator<ByteBuffer> itr = mReceivedBuffer.listIterator();
        while (itr.hasNext()) {
            ByteBuffer buff = itr.next();
            int copied = Math.min(ret.remaining(), buff.remaining());
            if (ret.remaining() < buff.remaining()) {
                byte[] tmp = new byte[copied];
                buff.get(tmp);
                ret.put(tmp);
            } else {
                ret.put(buff);
            }

            if (buff.remaining() == 0) {
                itr.remove();
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
