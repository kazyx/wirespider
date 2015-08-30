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
import net.kazyx.wirespider.util.BitMask;

import java.io.IOException;

class DeflateFilter implements PayloadFilter {
    private final PerMessageDeflate mDeflater;

    DeflateFilter(PerMessageDeflate deflater) {
        mDeflater = deflater;
    }

    @Override
    public byte[] onSendingText(byte[] barr, byte[] extensionBits) throws IOException {
        return onSendingMessage(barr, extensionBits);
    }

    @Override
    public byte[] onSendingBinary(byte[] data, byte[] extensionBits) throws IOException {
        return onSendingMessage(data, extensionBits);
    }

    @Override
    public byte[] onReceivingText(byte[] data, byte extensionBits) throws IOException {
        return onReceivingMessage(data, extensionBits);
    }

    @Override
    public byte[] onReceivingBinary(byte[] data, byte extensionBits) throws IOException {
        return onReceivingMessage(data, extensionBits);
    }

    private byte[] onSendingMessage(byte[] barr, byte[] extensionBits) throws IOException {
        byte[] compressed = mDeflater.compress(barr);
        if (compressed.length < barr.length) {
            extensionBits[0] = (byte) (extensionBits[0] | PerMessageCompression.RESERVED_BIT_FLAGS);
            return compressed;
        }
        return barr;
    }

    private byte[] onReceivingMessage(byte[] data, byte extensionBits) throws IOException {
        if (BitMask.isMatched(extensionBits, PerMessageCompression.RESERVED_BIT_FLAGS)) {
            return mDeflater.decompress(data);
        }
        return data;
    }
}
