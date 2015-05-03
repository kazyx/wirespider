package net.kazyx.apti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

class Rfc6455Handshake implements Handshake {
    private static final String TAG = Rfc6455Handshake.class.getSimpleName();
    private String mSecret;

    private final SocketChannelProxy mSocketChannelProxy;

    private final boolean mIsClient;

    Rfc6455Handshake(SocketChannelProxy proxy, boolean isClient) {
        mIsClient = isClient;
        mSocketChannelProxy = proxy;
    }

    @Override
    public void tryUpgrade(URI uri, List<HttpHeader> requestHeaders) {
        if (!mIsClient) {
            throw new UnsupportedOperationException("Upgrade request can only be sent from client side.");
        }
        mSecret = HandshakeSecretUtil.newSecretKey();

        StringBuilder sb = new StringBuilder();
        sb.append("GET ").append(TextUtil.isNullOrEmpty(uri.getPath()) ? "/" : uri.getPath())
                .append(" HTTP/1.1\r\n")
                .append("Host: ").append(uri.getHost()).append("\r\n")
                .append("Upgrade: websocket\r\n")
                .append("Connection: Upgrade\r\n")
                .append("Sec-WebSocket-Key: ").append(mSecret).append("\r\n")
                .append("Sec-WebSocket-Version: 13\r\n");

        if (requestHeaders != null && requestHeaders.size() != 0) {
            for (HttpHeader header : requestHeaders) {
                sb.append(header.toHeaderLine()).append("\r\n");
            }
        }

        sb.append("\r\n");

        mSocketChannelProxy.writeAsync(ByteArrayUtil.fromText(sb.toString()), true);
    }

    private final ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();

    @Override
    public LinkedList<ByteBuffer> onHandshakeResponse(LinkedList<ByteBuffer> data) throws BufferUnsatisfiedException, HandshakeFailureException {
        boolean isHeaderEnd = false;

        ListIterator<ByteBuffer> itr = data.listIterator();
        while (itr.hasNext()) {
            ByteBuffer buff = itr.next();
            byte[] ba = new byte[buff.remaining()];
            buff.get(ba);

            String str = ByteArrayUtil.toText(ba);
            // AptiLog.d(TAG, str);

            int index = str.indexOf("\r\n\r\n");
            if (index == -1) {
                mBuffer.write(ba, 0, ba.length);
                itr.remove();
            } else {
                isHeaderEnd = true;
                int end = index + 4;
                mBuffer.write(ba, 0, end);
                if (ba.length > end) {
                    buff.position(end + 1);
                } else {
                    itr.remove();
                }
                break;
            }
            // TODO if header is separated to multiple ByteBuffer
        }

        if (isHeaderEnd) {
            parseHeader(mBuffer.toByteArray());
            return data;
        } else {
            AptiLog.d(TAG, "Header unsatisfied");
            throw new BufferUnsatisfiedException();
        }
    }

    private void parseHeader(byte[] data) throws HandshakeFailureException {
        try {
            HttpHeaderReader headerReader = new HttpHeaderReader(data);

            HttpStatusLine statusLine = headerReader.statusLine();
            if (statusLine.statusCode() != 101) {
                throw new HandshakeFailureException("HTTP Status code not 101: " + statusLine.statusCode());
            }

            List<HttpHeader> resHeaders = headerReader.headerFields();

            boolean validated = false;
            for (HttpHeader header : resHeaders) {
                if (header.key.equalsIgnoreCase(HttpHeader.SEC_WEBSOCKET_ACCEPT)) {
                    String expected = HandshakeSecretUtil.scrambleSecret(mSecret);
                    validated = header.values.get(0).equals(expected);
                    if (!validated) {
                        throw new HandshakeFailureException("Invalid Sec-WebSocket-Accept header value");
                    }
                    break;
                }
            }

            if (!validated) {
                throw new HandshakeFailureException("No Sec-WebSocket-Accept header");
            }
        } catch (IOException e) {
            throw new HandshakeFailureException(e);
        }
    }
}
