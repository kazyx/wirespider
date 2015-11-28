/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * TCP connection.
 */
interface Session extends Closeable {
    /**
     * Read data into the given {@link ByteBuffer}
     *
     * @param readBuffer The buffer into which bytes are to be transferred
     * @return The number of bytes read, possibly zero, or <tt>-1</tt> if the
     * channel has reached end-of-stream
     * @throws IOException If some other I/O error occurs
     */
    int read(ByteBuffer readBuffer) throws IOException;

    /**
     * Write data from the given {@link ByteBuffer}
     *
     * @param buffer The buffer from which bytes are to be retrieved
     * @return The number of bytes written, possibly zero
     * @throws IOException If some other I/O error occurs
     */
    int write(ByteBuffer buffer) throws IOException;
}
