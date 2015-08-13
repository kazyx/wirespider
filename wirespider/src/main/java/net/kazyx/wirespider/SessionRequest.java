package net.kazyx.wirespider;

import net.kazyx.wirespider.delegate.HandshakeResponseHandler;
import net.kazyx.wirespider.delegate.SocketBinder;
import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.http.HttpHeader;
import net.kazyx.wirespider.util.ArgumentCheck;

import java.net.URI;
import java.util.Collections;
import java.util.List;

public final class SessionRequest {

    private SessionRequest(Builder builder) {
        this.mUri = builder.uri;
        this.mHandler = builder.handler;
        this.mMaxResponsePayloadSize = builder.maxResponsePayloadSize;
        this.mSocketBinder = builder.socketBinder;
        if (builder.headers != null) {
            this.mHeaders = Collections.unmodifiableList(builder.headers);
        }
        if (builder.extensions != null) {
            this.mExtensions = Collections.unmodifiableList(builder.extensions);
        }
        mProtocols = builder.protocols;
        mHsHandler = builder.hsHandler;
    }

    private URI mUri;

    public URI uri() {
        return mUri;
    }

    private WebSocketHandler mHandler;

    public WebSocketHandler handler() {
        return mHandler;
    }

    private int mMaxResponsePayloadSize;

    public int maxResponsePayloadSizeInBytes() {
        return mMaxResponsePayloadSize;
    }

    private SocketBinder mSocketBinder;

    public SocketBinder socketBinder() {
        return mSocketBinder;
    }

    private List<HttpHeader> mHeaders;

    public List<HttpHeader> headers() {
        return mHeaders;
    }

    private List<ExtensionRequest> mExtensions;

    public List<ExtensionRequest> extensions() {
        return mExtensions;
    }

    private List<String> mProtocols;

    public List<String> protocols() {
        return mProtocols;
    }

    private HandshakeResponseHandler mHsHandler;

    public HandshakeResponseHandler handshakeHandler() {
        return mHsHandler;
    }

    public static class Builder {
        private final URI uri;
        private final WebSocketHandler handler;

        /**
         * @param uri URI of the remote server.
         * @param handler WebSocket connection event handler.
         */
        public Builder(URI uri, WebSocketHandler handler) {
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
         * Create a {@link SessionRequest} with current configurations.
         *
         * @return Newly created {@link SessionRequest}
         */
        public SessionRequest build() {
            return new SessionRequest(this);
        }
    }
}
