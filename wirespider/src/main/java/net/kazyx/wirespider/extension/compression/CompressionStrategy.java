/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension.compression;

/**
 * Defines which sending messages to be compressed.
 */
public interface CompressionStrategy {
    /**
     * Specify the minimum size of the messages which are the target of compression.
     * <p>
     * Any messages are the target by default strategy.
     * </p>
     *
     * @return Minimum size of compression target messages in bytes.
     */
    int minSizeInBytes();
}
