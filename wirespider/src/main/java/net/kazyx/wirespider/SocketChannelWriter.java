/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

interface SocketChannelWriter {
    /**
     * Write data into the SocketChannel.<br>
     * Equivalent to {@code writeAsync(data, false);}
     *
     * @param data Data to write.
     */
    void writeAsync(byte[] data);

    /**
     * Write data into the SocketChannel.
     *
     * @param calledOnSelectorThread {@code true} to invoke this on the selector's thread.
     */
    void writeAsync(byte[] data, boolean calledOnSelectorThread);
}
