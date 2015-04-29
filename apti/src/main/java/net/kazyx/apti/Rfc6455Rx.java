package net.kazyx.apti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.ListIterator;

class Rfc6455Rx implements FrameRx {
    private static final String TAG = Rfc6455Rx.class.getSimpleName();

    private final WebSocket mWebSocket;

    Rfc6455Rx(WebSocket websocket) {
        mWebSocket = websocket;
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
                synchronized (mOperationSequenceLock) {
                    mWaitingSize = 1;
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
            try {
                byte second = readBytes(1)[0];
                isMasked = BitMask.isMatched(second, BitMask.BYTE_SYM_0x80);

                // TODO support large payload over 2GB
                payloadLength = second & BitMask.BYTE_SYM_0x7F;
                if (payloadLength == 0) {
                    throw new ProtocolViolationException("Payload length zero");
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
                }
            } catch (BufferUnsatisfiedException e) {
                synchronized (mOperationSequenceLock) {
                    mWaitingSize = 1;
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException e) {
                mWebSocket.onProtocolViolation();
            }
        }
    };

    private final Runnable mExtendedPayloadOperation = new Runnable() {
        @Override
        public void run() {
            int size = payloadLength == 126 ? 2 : 8;
            try {
                payloadLength = ByteArrayUtil.toUnsignedInteger(readBytes(size));
            } catch (BufferUnsatisfiedException e) {
                synchronized (mOperationSequenceLock) {
                    mWaitingSize = size;
                    mSuspendedOperation = this;
                }
            } catch (ProtocolViolationException e) {
                mWebSocket.onProtocolViolation();
            }
        }
    };

    byte[] mask;

    private final Runnable mMaskKeyOperation = new Runnable() {
        @Override
        public void run() {
            try {
                mask = readBytes(4);
                mPayloadOperation.run();
            } catch (BufferUnsatisfiedException e) {
                synchronized (mOperationSequenceLock) {
                    mWaitingSize = 4;
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
                synchronized (mOperationSequenceLock) {
                    mWaitingSize = payloadLength;
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
                int code = (payload.length >= 2) ? (payload[0] << 8) + payload[1] : CloseStatusCode.NO_STATUS_RECEIVED.statusCode;
                String reason = (payload.length > 2) ? ByteArrayUtil.toText(ByteArrayUtil.toSubArray(payload, 2)) : "";
                mWebSocket.onCloseFrame(code, reason);
                break;
            default:
                throw new ProtocolViolationException("Bad opcode: " + opcode);
        }
    }

    public void onDataReceived(LinkedList<ByteBuffer> data) {
        Logger.d(TAG, "onDataReceived");
        mReceivedBuffer.addAll(data);
        for (ByteBuffer buff : data) {
            mBufferSize += buff.limit();
        }

        synchronized (mOperationSequenceLock) {
            if (mWaitingSize < mBufferSize) {
                mSuspendedOperation.run();
            }
        }
    }

    private final Object mOperationSequenceLock = new Object();

    private Runnable mSuspendedOperation;

    private int mWaitingSize = 0;

    private int mBufferSize = 0;

    private final LinkedList<ByteBuffer> mReceivedBuffer = new LinkedList<>();

    private byte[] readBytes(int length) throws BufferUnsatisfiedException {
        if (mBufferSize < length) {
            mWaitingSize = length;
            throw new BufferUnsatisfiedException();
        }

        ByteBuffer dest = ByteBuffer.allocate(length);

        ListIterator<ByteBuffer> itr = mReceivedBuffer.listIterator();
        while (itr.hasNext()) {
            ByteBuffer buff = itr.next();
            if (buff.remaining() == 0) {
                itr.remove();
            }
            if (dest.remaining() == 0) {
                break;
            }
        }

        mBufferSize -= length;
        return dest.array();
    }
}
