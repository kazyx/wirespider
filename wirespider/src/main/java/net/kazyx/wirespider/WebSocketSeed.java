package net.kazyx.wirespider;

import java.net.URI;
import java.util.Collections;
import java.util.List;

public final class WebSocketSeed {

    WebSocketSeed(URI uri, WebSocketConnection handler, int maxResponsePayloadSize, SocketBinder socketBinder, List<HttpHeader> headers) {
        this.mUri = uri;
        this.mHandler = handler;
        this.mMaxResponsePayloadSize = maxResponsePayloadSize;
        this.mSocketBinder = socketBinder;
        if (headers != null) {
            this.mHeaders = Collections.unmodifiableList(headers);
        }
    }

    private URI mUri;

    URI uri() {
        return mUri;
    }

    private WebSocketConnection mHandler;

    WebSocketConnection handler() {
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

    public static class Builder {
        private final URI uri;
        private final WebSocketConnection handler;

        /**
         * @param uri     URI of the remote server.
         * @param handler WebSocket connection event handler.
         */
        public Builder(URI uri, WebSocketConnection handler) {
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

        /**
         * Create a {@link WebSocketSeed} with current configurations.
         *
         * @return Newly created {@link WebSocketSeed}
         */
        public WebSocketSeed build() {
            return new WebSocketSeed(uri, handler, maxResponsePayloadSize, socketBinder, headers);
        }
    }
}
