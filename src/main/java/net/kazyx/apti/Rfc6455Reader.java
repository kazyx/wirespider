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
            e.printStackTrace();
        }
    }

    private void readSingleFrame() throws IOException, ProtocolViolationException {
        byte first = readBytes(1)[0];
        boolean isFinal = Rfc6455Parser.asIsFinal(first);
        int opcode = Rfc6455Parser.asOpcode(first);

        byte secondByte = readBytes(1)[0];
        boolean isMasked = Rfc6455Parser.asIsMasked(secondByte);

        int payloadLength = Rfc6455Parser.asPayloadLength(secondByte);
        switch (payloadLength) {
            case 126:
                payloadLength = Rfc6455Parser.asExtendedPayloadLength(readBytes(2));
                break;
            case 127:
                payloadLength = Rfc6455Parser.asExtendedPayloadLength(readBytes(8));
                break;
        }

        byte[] mask = null;
        if (isMasked) {
            mask = readBytes(4);
        }

        byte[] payload = readBytes(payloadLength);
        if (isMasked) {
            payload = Rfc6455Parser.mask(payload, mask, 0);
        }

        handleFrame(opcode, payload, isFinal);
    }

    private enum ContinuationMode {
        TEXT,
        BINARY,
        UNSET,
    }

    private ContinuationMode mContinuation = ContinuationMode.UNSET;

    private void handleFrame(int opcode, byte[] payload, boolean isFinal) throws ProtocolViolationException, IOException {
        switch (opcode) {
            case Rfc6455Parser.OP_CONTINUATION:
                if (mContinuation == ContinuationMode.UNSET) {
                    throw new ProtocolViolationException("Sudden continuation opcode");
                }
                mPayloadBuffer.write(payload);
                if (isFinal) {
                    byte[] binary = mPayloadBuffer.toByteArray();
                    mPayloadBuffer.reset();
                    if (mContinuation == ContinuationMode.BINARY) {
                        mWebSocket.onBinaryMessage(binary);
                    } else {
                        mWebSocket.onTextMessage(ByteArrayUtil.toText(binary));
                    }
                }
                break;
            case Rfc6455Parser.OP_TEXT:
                if (isFinal) {
                    String text = ByteArrayUtil.toText(payload);
                    mWebSocket.onTextMessage(text);
                } else {
                    mPayloadBuffer.write(payload);
                    mContinuation = ContinuationMode.TEXT;
                }
                break;
            case Rfc6455Parser.OP_BINARY:
                if (isFinal) {
                    mWebSocket.onBinaryMessage(payload);
                } else {
                    mPayloadBuffer.write(payload);
                    mContinuation = ContinuationMode.BINARY;
                }
                break;
            case Rfc6455Parser.OP_PING:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for ping opcode");
                }
                if (payload.length > 125) {
                    throw new ProtocolViolationException("Ping payload too large");
                }
                mWebSocket.onPingFrame(ByteArrayUtil.toText(payload));
                break;
            case Rfc6455Parser.OP_PONG:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for pong opcode");
                }
                mWebSocket.onPongFrame(ByteArrayUtil.toText(payload));
                break;
            case Rfc6455Parser.OP_CLOSE:
                if (!isFinal) {
                    throw new ProtocolViolationException("Non-final flag for closeAsync opcode");
                }
                int code = (payload.length >= 2) ? 256 * payload[0] + payload[1] : CloseStatusCode.GENERAL.statusCode;
                String reason = (payload.length > 2) ? ByteArrayUtil.toText(ByteArrayUtil.toSubArray(payload, 2)) : "";
                mWebSocket.onCloseFrame(code, reason);
                break;
            default:
                throw new ProtocolViolationException("Bad opcode: " + opcode);
        }
    }

    private final ByteArrayOutputStream mPayloadBuffer = new ByteArrayOutputStream();

    private final byte[] mReadBuffer = new byte[1024];

    private byte[] readBytes(int length) throws IOException {
        mStream.read(mReadBuffer, 0, length);
        byte[] ret = new byte[length];
        System.arraycopy(mReadBuffer, 0, ret, 0, length);
        return ret;
    }
}
