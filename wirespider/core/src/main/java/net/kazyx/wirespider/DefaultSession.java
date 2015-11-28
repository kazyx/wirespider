/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Unsecured TCP connection.
 */
class DefaultSession implements Session {
    private final SocketChannel mChannel;

    DefaultSession(SocketChannel ch) {
        mChannel = ch;
    }

    @Override
    public int read(ByteBuffer buffer) throws IOException {
        return mChannel.read(buffer);
    }

    @Override
    public int write(ByteBuffer buffer) throws IOException {
        return mChannel.write(buffer);
    }

    @Override
    public void close() throws IOException {
        mChannel.close();
    }
}
