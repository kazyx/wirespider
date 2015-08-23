/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension.compression;

import net.kazyx.wirespider.extension.Extension;
import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.http.HttpHeader;

/**
 * Suggestion to use permessage-deflate extension in opening handshake.
 */
public class DeflateRequest implements ExtensionRequest {
    private final int mMaxClientWindowBits;

    private final int mMaxServerWindowBits;

    private final CompressionStrategy mStrategy;

    private DeflateRequest(Builder builder) {
        mMaxClientWindowBits = builder.mMaxClientWindowBits;
        mMaxServerWindowBits = builder.mMaxServerWindowBits;
        mStrategy = builder.mStrategy;
    }

    @Override
    public HttpHeader requestHeader() {
        StringBuilder sb = new StringBuilder(PerMessageDeflate.NAME)
                .append(";").append(PerMessageDeflate.CLIENT_NO_CONTEXT_TAKEOVER)
                .append(";").append(PerMessageDeflate.SERVER_NO_CONTEXT_TAKEOVER);
        if (mMaxClientWindowBits != 15) {
            sb.append(";").append(PerMessageDeflate.CLIENT_MAX_WINDOW_BITS)
                    .append("=").append(mMaxClientWindowBits);
        }
        if (mMaxServerWindowBits != 15) {
            sb.append(";").append(PerMessageDeflate.SERVER_MAX_WINDOW_BITS)
                    .append("=").append(mMaxServerWindowBits);
        }
        return new HttpHeader.Builder(HttpHeader.SEC_WEBSOCKET_EXTENSIONS).appendValue(sb.toString()).build();
    }

    @Override
    public Extension extension() {
        return new PerMessageDeflate(mStrategy);
    }

    public static class Builder {
        private int mMaxClientWindowBits = 8;

        private int mMaxServerWindowBits = 8;

        public Builder maxClientWindowBits(int bits) {
            if (bits < 8 || 15 < bits) {
                throw new IllegalArgumentException("Windows bits must be between 8 to 15.");
            }
            mMaxClientWindowBits = bits;
            return this;
        }

        public Builder maxServerWindowBits(int bits) {
            if (bits < 8 || 15 < bits) {
                throw new IllegalArgumentException("Windows bits must be between 8 to 15.");
            }
            mMaxServerWindowBits = bits;
            return this;
        }

        private CompressionStrategy mStrategy;

        /**
         * @param strategy Strategy to define which message to be compressed.
         * @return This builder.
         */
        public Builder strategy(CompressionStrategy strategy) {
            mStrategy = strategy;
            return this;
        }

        public DeflateRequest build() {
            return new DeflateRequest(this);
        }
    }
}
