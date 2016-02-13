/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.secure;

import net.kazyx.wirespider.Session;
import net.kazyx.wirespider.util.IOUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

class SecureSession implements Session {
    private static final int WRITE_BUFFER_SIZE = 1024 * 4;

    private final SecureSocketChannel mChannel;

    SecureSession(SSLContext sslContext, SelectionKey key) throws IOException {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(true);

        mChannel = new SecureSocketChannel(key, sslEngine, WRITE_BUFFER_SIZE);
        mChannel.init();
    }

    @Override
    public void close() {
        IOUtil.close(mChannel);
    }

    @Override
    public void enqueueWrite(ByteBuffer buffer) throws IOException {
        mChannel.wrapAndEnqueue(buffer);
    }

    @Override
    public void onFlushReady() throws IOException {
        mChannel.flush();
    }

    @Override
    public void onReadReady() throws IOException {
        mChannel.onReadReady();
    }

    @Override
    public void setListener(Listener listener) {
        mChannel.setDataListener(listener);
    }
}
