package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.ExtensionRequest;

import java.net.URI;
import java.util.Collections;
import java.util.List;

public final class WebSocketSeed {

    WebSocketSeed(URI uri, InterpretedEventHandler handler, int maxResponsePayloadSize, SocketBinder socketBinder
            , List<HttpHeader> headers, List<ExtensionRequest> extensions, List<String> protocols
            , HandshakeResponseHandler hsHandler) {
        this.mUri = uri;
        this.mHandler = handler;
        this.mMaxResponsePayloadSize = maxResponsePayloadSize;
        this.mSocketBinder = socketBinder;
        if (headers != null) {
            this.mHeaders = Collections.unmodifiableList(headers);
        }
        if (extensions != null) {
            this.mExtensions = Collections.unmodifiableList(extensions);
        }
        mProtocols = protocols;
        mHsHandler = hsHandler;
    }

    private URI mUri;

    URI uri() {
        return mUri;
    }

    private InterpretedEventHandler mHandler;

    InterpretedEventHandler handler() {
        return mHandler;
    }

    private int mMaxResponsePayloadSize;

    int maxResponsePayloadSizeInBytes() {
        return mMaxResponsePayloadSize;
    }

    private SocketBinder mSocketBinder;

    SocketBinder socketBinder() {
        return mSocketBinder;
    }

    private List<HttpHeader> mHeaders;

    List<HttpHeader> headers() {
        return mHeaders;
    }

    private List<ExtensionRequest> mExtensions;

    List<ExtensionRequest> extensions() {
        return mExtensions;
    }

    private List<String> mProtocols;

    List<String> protocols() {
        return mProtocols;
    }

    private HandshakeResponseHandler mHsHandler;

    HandshakeResponseHandler handshakeHandler() {
        return mHsHandler;
    }

    public static class Builder {
        private final URI uri;
        private final InterpretedEventHandler handler;

        /**
         * @param uri URI of the remote server.
         * @param handler WebSocket connection event handler.
         */
        public Builder(URI uri, InterpretedEventHandler handler) {
            ArgumentCheck.rejectNullArgs(uri, handler);
            this.uri = uri;
            this.handler = handler;
        }


        private int maxResponsePayloadSize = 65536;

        /**
         * Set maximum size of response payload.
         *
         * @param size Maximum size in bytes.
         * @return This builder.
         */
        public Builder maxResponsePayloadSizeInBytes(int size) {
            if (size < 1) {
                throw new IllegalArgumentException("Payload size must be positive value");
            }
            maxResponsePayloadSize = size;
            return this;
        }

        private SocketBinder socketBinder;

        /**
         * Set {@link SocketBinder} to be used before opening socket connection.
         *
         * @param binder SocketBinder.
         * @return This builder.
         */
        public Builder socketBinder(SocketBinder binder) {
            socketBinder = binder;
            return this;
        }

        private List<HttpHeader> headers;

        /**
         * @param cookies Additional HTTP header to be inserted to opening request.
         * @return This builder.
         */
        public Builder headers(List<HttpHeader> cookies) {
            this.headers = cookies;
            return this;
        }

        private List<ExtensionRequest> extensions;

        /**
         * @param extensions WebSocket extension request.
         * @return This builder.
         */
        public Builder extensions(List<ExtensionRequest> extensions) {
            this.extensions = extensions;
            return this;
        }

        private List<String> protocols;

        /**
         * @param protocols List of sub-protocol candidates.
         * @return This builder.
         */
        public Builder protocols(List<String> protocols) {
            this.protocols = protocols;
            return this;
        }

        private HandshakeResponseHandler hsHandler;

        /**
         * @param handler Handler to check handshake response
         * @return This builder.
         */
        public Builder handshakeHandler(HandshakeResponseHandler handler) {
            this.hsHandler = handler;
            return this;
        }

        /**
         * Create a {@link WebSocketSeed} with current configurations.
         *
         * @return Newly created {@link WebSocketSeed}
         */
        public WebSocketSeed build() {
            return new WebSocketSeed(uri, handler, maxResponsePayloadSize, socketBinder, headers, extensions, protocols, hsHandler);
        }
    }
}
