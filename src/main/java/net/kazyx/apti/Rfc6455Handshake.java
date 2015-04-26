package net.kazyx.apti;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

class Rfc6455Handshake implements Handshake {
    private String mSecret;

    @Override
    public void tryUpgrade(URI uri, List<HttpHeader> requestHeaders, SelectionHandler handler) {
        mSecret = HandshakeSecretUtil.createNew();

        StringBuilder sb = new StringBuilder();
        sb.append("GET ").append(uri.getSchemeSpecificPart() == null ? "/" : uri.getSchemeSpecificPart()).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(uri.getHost()).append("\r\n");
        sb.append("Upgrade: websocket\r\n");
        sb.append("Connection: Upgrade\r\n");
        sb.append("Sec-WebSocket-Key: ").append(mSecret).append("\r\n");
        sb.append("Sec-WebSocket-Version: 13\r\n");

        if (requestHeaders != null && requestHeaders.size() != 0) {
            for (HttpHeader header : requestHeaders) {
                sb.append(header.toHeaderLine()).append("\r\n");
            }
        }

        sb.append("\r\n");

        try {
            handler.writeAsync(sb.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // TODO
            e.printStackTrace();
        }
    }

    private final ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();

    @Override
    public LinkedList<ByteBuffer> onDataReceived(LinkedList<ByteBuffer> data) throws BufferUnsatisfiedException, HandshakeFailureException {
        boolean isLineEnd = false;

        boolean isHeaderEnd = false;

        ListIterator<ByteBuffer> itr = data.listIterator();
        while (itr.hasNext()) {
            ByteBuffer buff = itr.next();
            byte[] ba = new byte[buff.remaining()];
            buff.get(ba);

            int i;
            for (i = 0; i < ba.length; i++) {
                if (ba[i] == '\n') {
                    if (isLineEnd) {
                        isHeaderEnd = true;
                        break;
                    } else {
                        isLineEnd = true;
                    }
                } else {
                    isLineEnd = false;
                }
            }
            mBuffer.write(ba, 0, i);

            if (isHeaderEnd) {
                buff.position(i);
                break;
            } else {
                itr.remove();
            }
        }

        if (isHeaderEnd) {
            parseHeader(mBuffer.toByteArray());
            return data;
        } else {
            throw new BufferUnsatisfiedException();
        }
    }

    private void parseHeader(byte[] data) throws HandshakeFailureException {
        try {
            HttpHeaderReader headerReader = new HttpHeaderReader(data);

            HttpStatusLine statusLine = headerReader.getStatusLine();
            if (statusLine.statusCode != 101) {
                throw new HandshakeFailureException("WebSocket opening handshake failed: " + statusLine.statusCode);
            }

            List<HttpHeader> resHeaders = headerReader.getHeaderFields();

            boolean validated = false;
            for (HttpHeader header : resHeaders) {
                if (header.key.equalsIgnoreCase(HttpHeader.SEC_WEBSOCKET_ACCEPT)) {
                    String expected = HandshakeSecretUtil.convertForValidation(mSecret);
                    validated = header.values.get(0).equals(expected);
                    if (!validated) {
                        throw new HandshakeFailureException("WebSocket opening handshake failed: Invalid Sec-WebSocket-Accept header value");
                    }
                    break;
                }
            }

            if (!validated) {
                throw new HandshakeFailureException("WebSocket opening handshake failed: No Sec-WebSocket-Accept header");
            }
        } catch (IOException e) {
            throw new HandshakeFailureException(e);
        }
    }
}
