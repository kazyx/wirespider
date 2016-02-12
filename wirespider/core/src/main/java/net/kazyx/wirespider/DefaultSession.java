/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.SelectionKeyUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Insecure TCP connection.
 */
class DefaultSession implements Session {
    private final SelectionKey mKey;
    private final SocketChannel mChannel;

    private static final int READ_BUFFER_SIZE = 1024 * 4;
    private final ByteBuffer mReadBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);

    private static final int WRITE_BUFFER_SIZE = 1024 * 4;
    private final ByteBuffer mWriteBuffer = ByteBuffer.allocateDirect(WRITE_BUFFER_SIZE);

    private final Deque<ByteBuffer> mWriteQueue = new ArrayDeque<>();

    private final Object mLock = new Object();

    private Listener mListener;

    DefaultSession(SelectionKey key) {
        mKey = key;
        mChannel = (SocketChannel) key.channel();
    }

    @Override
    public void enqueueWrite(ByteBuffer data) throws IOException {
        if (!mKey.isValid()) {
            throw new IOException("SelectionKey is invalid");
        }

        synchronized (mLock) {
            mWriteQueue.addLast(data);
            if (mKey.interestOps() != (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
                SelectionKeyUtil.interestOps(mKey, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                mKey.selector().wakeup();
            }
        }
    }

    @Override
    public void onFlushReady() throws IOException {
        synchronized (mLock) {
            while (mWriteBuffer.remaining() != 0 && !mWriteQueue.isEmpty()) {
                ByteBuffer data = mWriteQueue.getFirst();

                if (mWriteBuffer.remaining() < data.remaining()) {
                    byte[] tmp = new byte[mWriteBuffer.remaining()];
                    data.get(tmp);
                    mWriteBuffer.put(tmp);
                } else {
                    mWriteBuffer.put(data);
                    mWriteQueue.remove();
                }
            }
        }

        mWriteBuffer.flip();
        mChannel.write(mWriteBuffer);
        mWriteBuffer.compact();

        synchronized (mLock) {
            if (mWriteBuffer.position() == 0 && mWriteQueue.isEmpty()) {
                SelectionKeyUtil.interestOps(mKey, SelectionKey.OP_READ);
            }
        }
    }

    @Override
    public void onReadReady() throws IOException {
        while (true) {
            ByteBuffer buff = read();
            if (buff == null) {
                break;
            }
            if (mListener != null) {
                mListener.onAppDataReceived(buff);
            }
        }
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
        mListener.onConnected();
    }

    private ByteBuffer read() throws IOException {
        try {
            int length = mChannel.read(mReadBuffer);
            if (length == -1) {
                throw new IOException("EOF");
            } else if (length == 0) {
                return null;
            } else {
                mReadBuffer.flip();
                ByteBuffer buff = ByteBuffer.allocate(length);
                buff.put(mReadBuffer);
                buff.flip();
                return buff;
            }
        } finally {
            mReadBuffer.clear();
        }
    }

    @Override
    public void close() throws IOException {
        mChannel.close();
    }
}
