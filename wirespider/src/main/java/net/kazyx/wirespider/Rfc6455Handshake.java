package net.kazyx.wirespider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

class Rfc6455Handshake implements Handshake {
    private static final String TAG = Rfc6455Handshake.class.getSimpleName();
    private String mSecret;

    private final SocketChannelWriter mWriter;

    private final boolean mIsClient;

    Rfc6455Handshake(SocketChannelWriter writer, boolean isClient) {
        mIsClient = isClient;
        mWriter = writer;
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

        mWriter.writeAsync(ByteArrayUtil.fromText(sb.toString()), true);
    }

    private final ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();

    @Override
    public LinkedList<byte[]> onHandshakeResponse(LinkedList<byte[]> data) throws BufferUnsatisfiedException, HandshakeFailureException {
        boolean isHeaderEnd = false;

        ListIterator<byte[]> itr = data.listIterator();
        while (itr.hasNext()) {
            byte[] ba = itr.next();

            String str = ByteArrayUtil.toText(ba);
            // Log.d(TAG, str);

            int index = str.indexOf("\r\n\r\n");
            if (index == -1) {
                mBuffer.write(ba, 0, ba.length);
                itr.remove();
            } else {
                isHeaderEnd = true;
                int end = index + 4;
                mBuffer.write(ba, 0, end);

                if (ba.length > end) {
                    itr.set(Arrays.copyOfRange(ba, end, ba.length));
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
            Log.d(TAG, "Header unsatisfied");
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

            Map<String, HttpHeader> resHeaders = headerReader.headerFields();

            HttpHeader upgrade = resHeaders.get(HttpHeader.UPGRADE.toLowerCase(Locale.US));
            if (upgrade == null || !"websocket".equalsIgnoreCase(upgrade.values.get(0))) {
                throw new HandshakeFailureException("Upgrade header error");
            }

            HttpHeader connection = resHeaders.get(HttpHeader.CONNECTION.toLowerCase(Locale.US));
            if (connection == null || !"Upgrade".equalsIgnoreCase(connection.values.get(0))) {
                throw new HandshakeFailureException("Connection header error");
            }

            HttpHeader accept = resHeaders.get(HttpHeader.SEC_WEBSOCKET_ACCEPT.toLowerCase(Locale.US));
            if (accept == null || !HandshakeSecretUtil.scrambleSecret(mSecret).equals(accept.values.get(0))) {
                throw new HandshakeFailureException("Sec-WebSocket-Accept header error");
            }
        } catch (IOException e) {
            throw new HandshakeFailureException(e);
        }
    }
}
