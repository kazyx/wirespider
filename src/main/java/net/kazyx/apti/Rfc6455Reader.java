package net.kazyx.apti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class Rfc6455Reader implements Runnable {

    private final InputStream mStream;
    private final WebSocket mWebSocket;

    Rfc6455Reader(InputStream stream, WebSocket websocket) {
        mStream = stream;
        mWebSocket = websocket;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && mStream.available() != -1) {
                readSingleFrame();
            }
        } catch (IOException e) {
            IOUtil.close(mStream);
        } catch (ProtocolViolationException e) {
            mWebSocket.onProtocolViolation();
            return;
        }
        mWebSocket.onCloseFrame(CloseStatusCode.ABNORMAL_CLOSURE.statusCode, "Finished with IOException");
    }

    private void readSingleFrame() throws IOException, ProtocolViolationException {
        byte first = readBytes(1)[0];
        boolean isFinal = BitMask.isMatched(first, Rfc6455.BIT_MASK_FIN);

        if ((first & Rfc6455.BIT_MASK_RSV) > 0) {
            throw new ProtocolViolationException("RSV non-zero");
        }
        int opcode = first & Rfc6455.BIT_MASK_OPCODE;

        byte second = readBytes(1)[0];
        boolean isMasked = BitMask.isMatched(second, Rfc6455.BIT_MASK_MASK);

        int payloadLength = second & Rfc6455.BIT_MASK_PAYLOAD_LENGTH;
        if (payloadLength == 0) {
            throw new ProtocolViolationException("Payload length zero");
        }

        switch (payloadLength) {
            case 126:
                payloadLength = ByteArrayUtil.toUnsignedInteger(readBytes(2));
                break;
            case 127:
                payloadLength = ByteArrayUtil.toUnsignedInteger(readBytes(8));
                break;
        }

        byte[] mask = null;
        if (isMasked) {
            mask = readBytes(4);
        }

        byte[] payload = readBytes(payloadLength);
        if (isMasked) {
            payload = BitMask.mask(payload, mask, 0);
        }

        handleFrame(opcode, payload, isFinal);
    }

    private enum ContinuationMode {
        TEXT,
        BINARY,
        UNSET,
    }

    private ContinuationMode mContinuation = ContinuationMode.UNSET;
    private final ByteArrayOutputStream mContinuationBuffer = new ByteArrayOutputStream();

    private void handleFrame(int opcode, byte[] payload, boolean isFinal) throws ProtocolViolationException, IOException {
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
                int code = (payload.length >= 2) ? 256 * payload[0] + payload[1] : CloseStatusCode.NO_STATUS_RECEIVED.statusCode;
                String reason = (payload.length > 2) ? ByteArrayUtil.toText(ByteArrayUtil.toSubArray(payload, 2)) : "";
                mWebSocket.onCloseFrame(code, reason);
                break;
            default:
                throw new ProtocolViolationException("Bad opcode: " + opcode);
        }
    }

    private final ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();
    private final byte[] mReadBuffer = new byte[1024];

    private byte[] readBytes(int length) throws IOException {
        int read = 0;
        while (read < length) {
            int tmp = mStream.read(mReadBuffer, 0, Math.min(mReadBuffer.length, length - read));
            if (tmp == -1) {
                throw new IOException("EOF detected");
            }
            mBuffer.write(mReadBuffer, 0, tmp);
            read += tmp;
        }
        byte[] ret = mBuffer.toByteArray();
        mBuffer.reset();
        return ret;
    }
}
