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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * permessage-compression extension.
 */
public abstract class PerMessageCompression implements Extension {

    public static final byte RESERVED_BIT_FLAGS = 0b01000000;

    /**
     * @param source Original data.
     * @return Compressed data.
     * @throws IOException Failed to compress data.
     */
    public abstract byte[] compress(byte[] source) throws IOException;

    /**
     * @param source Compressed data.
     * @return Original data.
     * @throws IOException Failed to decompress data.
     */
    public abstract ByteBuffer decompress(ByteBuffer source) throws IOException;
}
