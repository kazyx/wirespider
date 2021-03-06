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
import net.kazyx.wirespider.delegate.SocketBinder;
import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.http.HttpHeader;
import net.kazyx.wirespider.util.ArgumentCheck;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        mConnectionTimeout = builder.connTimeout;
        mConnTimeoutUnit = builder.connTimeoutUnit;
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

    private int mConnectionTimeout;

    public int connectionTimeout() {
        return mConnectionTimeout;
    }

    private TimeUnit mConnTimeoutUnit;

    public TimeUnit connectionTimeoutUnit() {
        return mConnTimeoutUnit;
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
        public Builder setMaxResponsePayloadSizeInBytes(int size) {
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
        public Builder setSocketBinder(SocketBinder binder) {
            socketBinder = binder;
            return this;
        }

        private List<HttpHeader> headers;

        /**
         * @param cookies Additional HTTP header to be inserted to opening request.
         * @return This builder.
         */
        public Builder setHeaders(List<HttpHeader> cookies) {
            this.headers = cookies;
            return this;
        }

        private List<? extends ExtensionRequest> extensions;

        /**
         * @param extensions WebSocket extension request.
         * @return This builder.
         */
        public Builder setExtensions(List<? extends ExtensionRequest> extensions) {
            this.extensions = extensions;
            return this;
        }

        private List<String> protocols;

        /**
         * @param protocols List of sub-protocol candidates.
         * @return This builder.
         */
        public Builder setProtocols(List<String> protocols) {
            this.protocols = protocols;
            return this;
        }

        private HandshakeResponseHandler hsHandler;

        /**
         * @param handler Handler to check handshake response
         * @return This builder.
         */
        public Builder setHandshakeHandler(HandshakeResponseHandler handler) {
            this.hsHandler = handler;
            return this;
        }

        private int connTimeout = 0;
        private TimeUnit connTimeoutUnit = TimeUnit.MILLISECONDS;

        /**
         * Set timeout to complete opening handshake. It is set infinite by default.
         *
         * @param timeout Timeout value.
         * @param unit Timeout unit.
         * @return This builder
         * @throws IllegalArgumentException If {@code timeout} is zero or negative value, or {@code unit} is {@code null}.
         */
        public Builder setConnectionTimeout(int timeout, TimeUnit unit) {
            if (timeout <= 0) {
                throw new IllegalArgumentException("Timeout value must be positive");
            }
            ArgumentCheck.rejectNull(unit);

            this.connTimeout = timeout;
            this.connTimeoutUnit = unit;
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
