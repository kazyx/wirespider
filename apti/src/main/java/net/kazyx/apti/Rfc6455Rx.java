package net.kazyx.apti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.ListIterator;

class Rfc6455Rx implements FrameRx {
    private static final String TAG = Rfc6455Rx.class.getSimpleName();

    private final WebSocket mWebSocket;
    private final int mMaxPayloadSize;

    Rfc6455Rx(WebSocket websocket, int maxPayload) {
        mWebSocket = websocket;
        mMaxPayloadSize = maxPayload;
    }

    private boolean isFinal;
    private byte opcode;

    private final Runnable mReadOpCodeOperation = new Runnable() {
        @Override
        public void run() {
            // AptiLog.d(TAG, "FirstByte operation");
            try {
                byte first = readBytes(1)[0];
                isFinal = BitMask.isMatched(first, BitMask.BYTE_SYM_0x80);

                if ((first & BitMask.BYTE_SYM_0x70) != 0) {
                    throw new ProtocolViolationException("RSV non-zero");
                }
                opcode = (byte) (first & BitMask.BYTE_SYM_0x0F);
                mSecondByteOperation.run();
            } catch (BufferUnsatisfiedException e) {
                // AptiLog.d(TAG, "BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException e) {
                mWebSocket.onProtocolViolation();
            }
        }
    };

    private boolean isMasked;
    private int payloadLength;

    private final Runnable mSecondByteOperation = new Runnable() {
        @Override
        public void run() {
            // AptiLog.d(TAG, "SecondByte operation");
            try {
                byte second = readBytes(1)[0];
                isMasked = BitMask.isMatched(second, BitMask.BYTE_SYM_0x80);

                payloadLength = second & BitMask.BYTE_SYM_0x7F;
                if (payloadLength > mMaxPayloadSize) {
                    throw new PayloadSizeOverflowException();
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
                AptiLog.d(TAG, "SecondByte BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (PayloadSizeOverflowException e) {
                mWebSocket.onPayloadOverflow();
            }
        }
    };

    private final Runnable mExtendedPayloadOperation = new Runnable() {
        @Override
        public void run() {
            // AptiLog.d(TAG, "ExtendedPayloadLength operation");
            int size = payloadLength == 126 ? 2 : 8;
            try {
                // TODO support large payload over 2GB
                payloadLength = ByteArrayUtil.toUnsignedInteger(readBytes(size));
                if (payloadLength > mMaxPayloadSize) {
                    throw new PayloadSizeOverflowException();
                }
                if (isMasked) {
                    mMaskKeyOperation.run();
                } else {
                    mPayloadOperation.run();
                }
            } catch (BufferUnsatisfiedException e) {
                AptiLog.d(TAG, "ExtendedPayloadLength BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException e) {
                AptiLog.d(TAG, "ExtendedPayloadLength ProtocolViolation");
                mWebSocket.onProtocolViolation();
            } catch (PayloadSizeOverflowException e) {
                mWebSocket.onPayloadOverflow();
            }
        }
    };

    private byte[] mask;

    private final Runnable mMaskKeyOperation = new Runnable() {
        @Override
        public void run() {
            // AptiLog.d(TAG, "MaskKey operation");
            try {
                mask = readBytes(4);
                mPayloadOperation.run();
            } catch (BufferUnsatisfiedException e) {
                AptiLog.d(TAG, "MaskKey BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            }
        }
    };

    private final Runnable mPayloadOperation = new Runnable() {
        @Override
        public void run() {
            // AptiLog.d(TAG, "Payload operation: " + payloadLength);
            try {
                byte[] payload = readBytes(payloadLength);
                if (isMasked) {
                    payload = BitMask.maskAll(payload, mask);
                }

                handleFrame(opcode, payload, isFinal);
                mReadOpCodeOperation.run();
            } catch (BufferUnsatisfiedException e) {
                AptiLog.d(TAG, "Payload BufferUnsatisfied");
                synchronized (mOperationSequenceLock) {
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException e) {
                mWebSocket.onProtocolViolation();
            } catch (IOException e) {
                // TODO
                e.printStackTrace();
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
        // AptiLog.d(TAG, "handleFrame: " + opcode);
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
                        mWebSocket.onBinaryMessage(binary);
                    } else {
                        mWebSocket.onTextMessage(ByteArrayUtil.toText(binary));
                    }
                }
                break;
            case OpCode.TEXT:
                if (isFinal) {
                    String text = ByteArrayUtil.toText(payload);
                    mWebSocket.onTextMessage(text);
                } else {
                    mContinuationBuffer.write(payload);
                    mContinuation = ContinuationMode.TEXT;
                }
                break;
            case OpCode.BINARY:
                if (isFinal) {
                    mWebSocket.onBinaryMessage(payload);
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
                mWebSocket.onPingFrame(ByteArrayUtil.toText(payload));
                break;
            case OpCode.PONG:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for pong opcode");
                }
                mWebSocket.onPongFrame(ByteArrayUtil.toText(payload));
                break;
            case OpCode.CONNECTION_CLOSE:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for closeAsync opcode");
                }
                int code = (payload.length >= 2) ? payload[1] & 0xFF + (payload[0] << 8) : CloseStatusCode.NO_STATUS_RECEIVED.statusCode;
                String reason = (payload.length > 2) ? ByteArrayUtil.toText(ByteArrayUtil.toSubArray(payload, 2)) : "";
                mWebSocket.onCloseFrame(code, reason);
                break;
            default:
                throw new ProtocolViolationException("Bad opcode: " + opcode);
        }
    }

    @Override
    public void onDataReceived(LinkedList<ByteBuffer> data) {
        // AptiLog.d(TAG, "onDataReceived");
        mReceivedBuffer.addAll(data);
        for (ByteBuffer buff : data) {
            mBufferSize += buff.limit();
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

    private final LinkedList<ByteBuffer> mReceivedBuffer = new LinkedList<>();

    private byte[] readBytes(int length) throws BufferUnsatisfiedException {
        if (mBufferSize < length) {
            mWaitingSize = length;
            throw new BufferUnsatisfiedException();
        }

        byte[] ba = new byte[length];
        int remaining = length;

        ListIterator<ByteBuffer> itr = mReceivedBuffer.listIterator();
        while (itr.hasNext()) {
            ByteBuffer buff = itr.next();
            int copied = Math.min(remaining, buff.remaining());
            buff.get(ba, length - remaining, copied);
            remaining -= copied;

            if (buff.remaining() == 0) {
                itr.remove();
            }
            if (remaining == 0) {
                break;
            }
        }

        mBufferSize -= length;

        return ba;
    }
}
