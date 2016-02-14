/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension.compression;

import net.kazyx.wirespider.extension.PayloadFilter;

import java.io.IOException;
import java.nio.ByteBuffer;

class DeflateFilter implements PayloadFilter {
    private final PerMessageDeflate mDeflater;

    DeflateFilter(PerMessageDeflate deflater) {
        mDeflater = deflater;
    }

    @Override
    public ByteBuffer onSendingText(ByteBuffer data, byte[] extensionBits) throws IOException {
        return onSendingMessage(data, extensionBits);
    }

    @Override
    public ByteBuffer onSendingBinary(ByteBuffer data, byte[] extensionBits) throws IOException {
        return onSendingMessage(data, extensionBits);
    }

    @Override
    public ByteBuffer onReceivingText(ByteBuffer data) throws IOException {
        return onReceivingMessage(data);
    }

    @Override
    public ByteBuffer onReceivingBinary(ByteBuffer data) throws IOException {
        return onReceivingMessage(data);
    }

    private ByteBuffer onSendingMessage(ByteBuffer data, byte[] extensionBits) throws IOException {
        int pos = data.position();
        int limit = data.limit();
        int remaining = data.remaining();
        ByteBuffer compressed = mDeflater.compress(data);
        if (compressed.remaining() < remaining) {
            extensionBits[0] = (byte) (extensionBits[0] | PerMessageCompression.RESERVED_BIT_FLAGS);
            return compressed;
        }
        data.position(pos);
        data.limit(limit);
        return data;
    }

    private ByteBuffer onReceivingMessage(ByteBuffer data) throws IOException {
        return mDeflater.decompress(data);
    }
}
