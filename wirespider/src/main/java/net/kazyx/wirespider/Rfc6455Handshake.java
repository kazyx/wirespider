/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.delegate.HandshakeResponseHandler;
import net.kazyx.wirespider.extension.Extension;
import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.http.HttpHeader;
import net.kazyx.wirespider.http.HttpHeaderReader;
import net.kazyx.wirespider.http.HttpStatusLine;
import net.kazyx.wirespider.util.ByteArrayUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

class Rfc6455Handshake implements Handshake {
    private static final String TAG = Rfc6455Handshake.class.getSimpleName();
    private String mSecret;

    private final SocketChannelWriter mWriter;

    private final List<Extension> mRequestedExtensions = new ArrayList<>();

    private final List<String> mProtocolCandidates = new ArrayList<>();

    private HandshakeResponse mResponse;

    private final boolean mIsClient;

    private HandshakeResponseHandler mResponseHandler;

    private static class DefaultHandshakeResponseHandler implements HandshakeResponseHandler {
        List<String> mProtocolList;

        DefaultHandshakeResponseHandler(List<String> protocolCandidates) {
            mProtocolList = protocolCandidates;
        }

        @Override
        public boolean onReceived(HandshakeResponse response) {
            if (mProtocolList == null || mProtocolList.size() == 0) {
                WsLog.d(TAG, "No protocol requested");
                return true;
            }

            if (response.protocol() == null) {
                WsLog.d(TAG, "No accepted protocol");
                return false;
            }

            for (String candidate : mProtocolList) {
                if (candidate.equalsIgnoreCase(response.protocol())) {
                    WsLog.d(TAG, "Protocol: " + response.protocol() + " has been accepted");
                    // OK one of the protocol candidate accepted.
                    return true;
                }
            }

            WsLog.d(TAG, "Unknown protocol received: " + response.protocol());
            return false;
        }
    }

    Rfc6455Handshake(SocketChannelWriter writer, boolean isClient) {
        mIsClient = isClient;
        mWriter = writer;
    }

    @Override
    public void responseHandler(HandshakeResponseHandler handler) {
        mResponseHandler = handler;
    }

    @Override
    public void tryUpgrade(URI uri, SessionRequest seed) {
        if (!mIsClient) {
            throw new UnsupportedOperationException("Upgrade request can only be sent from client side.");
        }
        mSecret = HandshakeSecretUtil.newSecretKey();

        StringBuilder sb = new StringBuilder();

        String path = uri.getPath();
        if (path == null || path.length() == 0) {
            path = "/";
        }
        sb.append("GET ").append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(uri.getHost()).append("\r\n")
                .append("Upgrade: websocket\r\n")
                .append("Connection: Upgrade\r\n")
                .append("Sec-WebSocket-Key: ").append(mSecret).append("\r\n")
                .append("Sec-WebSocket-Version: 13\r\n");
        List<ExtensionRequest> extensions = seed.extensions();
        if (extensions != null) {
            for (ExtensionRequest exReq : extensions) {
                sb.append(exReq.requestHeader().toHeaderLine()).append("\r\n");
                mRequestedExtensions.add(exReq.extension());
            }
        }

        List<HttpHeader> requestHeaders = seed.headers();
        if (requestHeaders != null) {
            for (HttpHeader header : requestHeaders) {
                String headerLine = header.toHeaderLine();
                sb.append(headerLine).append("\r\n");
            }
        }

        if (seed.protocols() != null) {
            for (String protocol : seed.protocols()) {
                sb.append(HttpHeader.SEC_WEBSOCKET_PROTOCOL).append(": ").append(protocol).append("\r\n");
                mProtocolCandidates.add(protocol);
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
            byte[] header = mBuffer.toByteArray();
            mBuffer.reset();
            parseHeader(header);
            return data;
        } else {
            WsLog.d(TAG, "Header unsatisfied");
            throw new BufferUnsatisfiedException();
        }
    }

    @Override
    public List<Extension> extensions() {
        return new ArrayList<>(mResponse.extensions());
    }

    @Override
    public String protocol() {
        return mResponse.protocol();
    }

    private void parseHeader(byte[] data) throws HandshakeFailureException {
        try {
            HttpHeaderReader headerReader = new HttpHeaderReader(data);

            HttpStatusLine statusLine = headerReader.statusLine();
            if (statusLine.statusCode() != 101) {
                throw new HandshakeFailureException("HTTP Status code not 101: " + statusLine.statusCode());
            }

            Map<String, HttpHeader> resHeaders = headerReader.headerFields();
            WsLog.v(TAG, "ResponseHeaders", resHeaders.toString());

            HttpHeader upgrade = resHeaders.get(HttpHeader.UPGRADE.toLowerCase(Locale.US));
            if (upgrade == null || !"websocket".equalsIgnoreCase(upgrade.values().get(0))) {
                throw new HandshakeFailureException("Upgrade header error");
            }

            HttpHeader connection = resHeaders.get(HttpHeader.CONNECTION.toLowerCase(Locale.US));
            if (connection == null || !"Upgrade".equalsIgnoreCase(connection.values().get(0))) {
                throw new HandshakeFailureException("Connection header error");
            }

            HttpHeader accept = resHeaders.get(HttpHeader.SEC_WEBSOCKET_ACCEPT.toLowerCase(Locale.US));
            if (accept == null || !HandshakeSecretUtil.scrambleSecret(mSecret).equals(accept.values().get(0))) {
                throw new HandshakeFailureException("Sec-WebSocket-Accept header error");
            }

            String protocol = parseProtocol(resHeaders.get(HttpHeader.SEC_WEBSOCKET_PROTOCOL.toLowerCase(Locale.US)));
            List<Extension> extension = parseExtensions(resHeaders.get(HttpHeader.SEC_WEBSOCKET_EXTENSIONS.toLowerCase(Locale.US)), mRequestedExtensions);

            mResponse = new HandshakeResponse(extension, protocol);

            HandshakeResponseHandler handler;
            if (mResponseHandler != null) {
                handler = mResponseHandler;
            } else {
                handler = new DefaultHandshakeResponseHandler(mProtocolCandidates);
            }
            if (!handler.onReceived(mResponse)) {
                throw new HandshakeFailureException("Handshake response rejected by handshake response handler");
            }
        } catch (IOException e) {
            throw new HandshakeFailureException(e);
        }
    }

    private static String parseProtocol(HttpHeader protocolHeader) {
        if (protocolHeader == null) {
            return null;
        }

        if (protocolHeader.values().size() != 1) {
            WsLog.d(TAG, "Multiple protocol header", protocolHeader.toHeaderLine());
        }

        return protocolHeader.values().get(0);
    }

    private static List<Extension> parseExtensions(HttpHeader extensionHeader, List<Extension> candidates) throws HandshakeFailureException {
        if (extensionHeader == null) {
            WsLog.v(TAG, "No extensions in response");
            return null;
        }

        WsLog.v(TAG, "parseExtensions: " + extensionHeader.toHeaderLine());
        List<Extension> acceptedExtensions = new ArrayList<>();

        for (String value : extensionHeader.values()) {
            String[] split = value.split(";");
            if (split.length == 0) {
                continue;
            }

            String name = split[0].trim();

            Iterator<Extension> itr = candidates.iterator();
            while (itr.hasNext()) {
                Extension ext = itr.next();
                if (ext.name().equals(name)) {
                    if (ext.accept(split)) {
                        acceptedExtensions.add(ext);
                        itr.remove();
                        break;
                    } else {
                        WsLog.d(TAG, "Unacceptable extension response", value);
                        throw new HandshakeFailureException("Unacceptable extension response");
                    }
                }
            }
        }

        return acceptedExtensions;
    }
}
