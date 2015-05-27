package net.kazyx.wirespider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

class Rfc6455Rx implements FrameRx {
    private static final String TAG = Rfc6455Rx.class.getSimpleName();

    private final FrameRx.Listener mListener;
    private final int mMaxPayloadSize;
    private final boolean mIsClient;

    Rfc6455Rx(FrameRx.Listener listener, int maxPayload, boolean isClient) {
        mListener = listener;
        mMaxPayloadSize = maxPayload;
        mIsClient = isClient;
    }

    private boolean isFinal;
    private byte opcode;

    private final Runnable mReadOpCodeOperation = new Runnable() {
        @Override
        public void run() {
            try {
                byte first = readBytes(1)[0];
                isFinal = BitMask.isMatched(first, BitMask.BYTE_SYM_0x80);

                if ((first & BitMask.BYTE_SYM_0x70) != 0) {
                    throw new ProtocolViolationException("RSV non-zero");
                }
                opcode = (byte) (first & BitMask.BYTE_SYM_0x0F);
                mSecondByteOperation.run();
            } catch (BufferUnsatisfiedException e) {
                // No need to flush this log. Always happens at frame end.
                // Log.d(TAG, "BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException e) {
                Log.d(TAG, "Protocol violation", e.getMessage());
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
                byte second = readBytes(1)[0];
                isMasked = BitMask.isMatched(second, BitMask.BYTE_SYM_0x80);

                if (!(mIsClient ^ isMasked)) {
                    throw new ProtocolViolationException("Masked payload from server or unmasked payload from client");
                }

                payloadLength = second & BitMask.BYTE_SYM_0x7F;
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
                Log.v(TAG, "SecondByte BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (PayloadSizeOverflowException e) {
                Log.d(TAG, "Payload size overflow", e.getMessage());
                mListener.onPayloadOverflow();
            } catch (ProtocolViolationException e) {
                Log.d(TAG, "Protocol violation", e.getMessage());
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
                Log.v(TAG, "ExtendedPayloadLength BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (PayloadSizeOverflowException e) {
                Log.d(TAG, "Payload size overflow", e.getMessage());
                mListener.onPayloadOverflow();
            }
        }
    };

    private byte[] mask;

    private final Runnable mMaskKeyOperation = new Runnable() {
        @Override
        public void run() {
            try {
                mask = readBytes(4);
                mPayloadOperation.run();
            } catch (BufferUnsatisfiedException e) {
                Log.v(TAG, "MaskKey BufferUnsatisfied");
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
                byte[] payload = readBytes(payloadLength);
                if (isMasked) {
                    payload = BitMask.maskAll(payload, mask);
                }

                handleFrame(opcode, payload, isFinal);
                mReadOpCodeOperation.run();
            } catch (BufferUnsatisfiedException e) {
                if (mSuspendedOperation != this) {
                    // Flush log only for the first time
                    Log.v(TAG, "Payload BufferUnsatisfied");
                }
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException e) {
                Log.d(TAG, "Protocol violation", e.getMessage());
                mListener.onProtocolViolation();
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

    private void handleFrame(byte opcode, byte[] payload, boolean isFinal) throws ProtocolViolationException, IOException {
        Log.v(TAG, "handleFrame", opcode);
        switch (opcode) {
            case OpCode.CONTINUATION:
                if (mContinuation == ContinuationMode.UNSET) {
                    throw new ProtocolViolationException("Sudden continuation opcode");
                }
                mContinuationBuffer.write(payload);
                if (isFinal) {
                    byte[] binary = mContinuationBuffer.toByteArray();
                    mContinuationBuffer.reset();
                    if (mContinuation == ContinuationMode.BINARY) {
                        mListener.onBinaryMessage(binary);
                    } else {
                        mListener.onTextMessage(ByteArrayUtil.toText(binary));
                    }
                    mContinuation = ContinuationMode.UNSET;
                }
                break;
            case OpCode.TEXT:
                if (isFinal) {
                    String text = ByteArrayUtil.toText(payload);
                    mListener.onTextMessage(text);
                } else {
                    mContinuationBuffer.write(payload);
                    mContinuation = ContinuationMode.TEXT;
                }
                break;
            case OpCode.BINARY:
                if (isFinal) {
                    mListener.onBinaryMessage(payload);
                } else {
                    mContinuationBuffer.write(payload);
                    mContinuation = ContinuationMode.BINARY;
                }
                break;
            case OpCode.PING:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for ping opcode");
                }
                if (payload.length > 125) {
                    throw new ProtocolViolationException("Ping payload too large");
                }
                mListener.onPingFrame(ByteArrayUtil.toText(payload));
                break;
            case OpCode.PONG:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for pong opcode");
                }
                mListener.onPongFrame(ByteArrayUtil.toText(payload));
                break;
            case OpCode.CONNECTION_CLOSE:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for close opcode");
                }
                int code = (payload.length >= 2) ? payload[1] & 0xFF + (payload[0] << 8) : CloseStatusCode.NO_STATUS_RECEIVED.statusCode;
                String reason = (payload.length > 2) ? ByteArrayUtil.toText(ByteArrayUtil.toSubArray(payload, 2)) : "";
                mListener.onCloseFrame(code, reason);
                break;
            default:
                throw new ProtocolViolationException("Bad opcode: " + opcode);
        }
    }

    @Override
    public void onDataReceived(LinkedList<byte[]> data) {
        // Log.d(TAG, "onDataReceived");
        mReceivedBuffer.addAll(data);
        for (byte[] buff : data) {
            mBufferSize += buff.length;
        }

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

    private final LinkedList<byte[]> mReceivedBuffer = new LinkedList<>();

    private byte[] readBytes(int length) throws BufferUnsatisfiedException {
        if (mBufferSize < length) {
            mWaitingSize = length;
            throw new BufferUnsatisfiedException();
        }

        byte[] ba = new byte[length];
        int remaining = length;

        ListIterator<byte[]> itr = mReceivedBuffer.listIterator();
        while (itr.hasNext()) {
            byte[] buff = itr.next();
            int copied = Math.min(remaining, buff.length);
            System.arraycopy(buff, 0, ba, length - remaining, copied);

            remaining -= copied;

            if (copied == buff.length) {
                itr.remove();
            } else {
                itr.set(Arrays.copyOfRange(buff, copied, buff.length));
            }
            if (remaining == 0) {
                break;
            }
        }

        mBufferSize -= length;

        return ba;
    }
}