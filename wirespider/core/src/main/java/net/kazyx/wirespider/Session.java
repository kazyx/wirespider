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
     * Write data from the given {@link ByteBuffer}
     *
     * @param buffer The buffer from which bytes are to be retrieved
     * @throws IOException If some other I/O error occurs
     */
    void enqueueWrite(ByteBuffer buffer) throws IOException;

    /**
     * Ready to write data into the SocketChannel.
     *
     * @throws IOException If some other I/O error occurs
     */
    void onFlushReady() throws IOException;

    /**
     * Ready to read data from the SocketChannel.
     *
     * @throws IOException If some other I/O error occurs
     */
    void onReadReady() throws IOException;

    /**
     * Set {@link Listener} to detect data reception.
     *
     * @param listener Data reception listener.
     */
    void setListener(Listener listener);

    interface Listener {
        /**
         * @param data Received application data.
         */
        void onAppDataReceived(ByteBuffer data);

        /**
         * Ready to handle application data.
         */
        void onConnected();
    }
}
