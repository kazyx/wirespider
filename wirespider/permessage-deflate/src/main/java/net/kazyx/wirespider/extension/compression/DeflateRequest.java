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
    // private final int mMaxClientWindowBits;

    private final int mMaxServerWindowBits;

    private int mCompressionThreshold;

    private DeflateRequest(Builder builder) {
        // mMaxClientWindowBits = builder.mMaxClientWindowBits;
        mMaxServerWindowBits = builder.mMaxServerWindowBits;
        mCompressionThreshold = builder.mCompressionThreshold;
    }

    @Override
    public HttpHeader requestHeader() {
        StringBuilder sb = new StringBuilder(PerMessageDeflate.NAME)
                .append(";").append(PerMessageDeflate.CLIENT_NO_CONTEXT_TAKEOVER)
                .append(";").append(PerMessageDeflate.SERVER_NO_CONTEXT_TAKEOVER);
        /*
        if (mMaxClientWindowBits != 15) {
            sb.append(";").append(PerMessageDeflate.CLIENT_MAX_WINDOW_BITS)
                    .append("=").append(mMaxClientWindowBits);
        }
        */
        if (mMaxServerWindowBits != 15) {
            sb.append(";").append(PerMessageDeflate.SERVER_MAX_WINDOW_BITS)
                    .append("=").append(mMaxServerWindowBits);
        }
        return new HttpHeader.Builder(HttpHeader.SEC_WEBSOCKET_EXTENSIONS).appendValue(sb.toString()).build();
    }

    @Override
    public Extension extension() {
        return new PerMessageDeflate(mCompressionThreshold);
    }

    public static class Builder {
        // private int mMaxClientWindowBits = 8;

        private int mMaxServerWindowBits = 8;

        /**
         * Inform server that client supports {@code "client_max_window_bits"} parameter and
         * hint that client will use up to representable unsigned integer with given bits
         * if negotiation response from server does not contain {@code "client_max_window_bits"} parameter.
         * <p>
         * <b>Note: This method always throws {@link UnsupportedOperationException}.</b>
         * </p>
         *
         * @param bits From 8 to 15. Number of bits to express an unsigned integer, which represents default maximum LZ77 sliding window size of client side.
         * @return This builder.
         * @throws UnsupportedOperationException Always.
         * @see <a href="https://tools.ietf.org/html/rfc7692#section-7.1.2.2">RFC 7692 Section 7.1.2.2</a>
         * @deprecated This method should not be used since LZ77 sliding window size is not exposed in Java.
         */
        @Deprecated
        public Builder setMaxClientWindowBits(int bits) {
            throw new UnsupportedOperationException("");
            /*
            if (bits < 8 || 15 < bits) {
                throw new IllegalArgumentException("Windows bits must be between 8 to 15.");
            }
            mMaxClientWindowBits = bits;
            return this;
            */
        }

        /**
         * Request server to use LZ77 sliding window size up to representable unsigned integer with given bits.<br>
         * Client can receive messages compressed using sliding window of up to 32,768 bytes (15 bits) by default.
         * <p>
         * Smaller window size will reduce memory usage for decompression.
         * </p>
         *
         * @param bits From 8 to 15. Number of bits to express an unsigned integer, which represents maximum LZ77 sliding window size of server side.
         * @return This builder.
         * @throws IllegalArgumentException If given value is less than 8 or more than 15.
         * @see <a href="https://tools.ietf.org/html/rfc7692#section-7.1.2.1">RFC 7692 Section 7.1.2.1</a>
         */
        public Builder setMaxServerWindowBits(int bits) {
            if (bits < 8 || 15 < bits) {
                throw new IllegalArgumentException("Windows bits must be between 8 to 15.");
            }
            mMaxServerWindowBits = bits;
            return this;
        }

        /**
         * 0 means compress any messages.
         */
        private int mCompressionThreshold = 0;

        /**
         * @param sizeInBytes Minimum size of messages to enable compression in bytes.
         * @return This builder
         */
        public Builder setCompressionThreshold(int sizeInBytes) {
            mCompressionThreshold = sizeInBytes;
            return this;
        }

        public DeflateRequest build() {
            return new DeflateRequest(this);
        }
    }
}
