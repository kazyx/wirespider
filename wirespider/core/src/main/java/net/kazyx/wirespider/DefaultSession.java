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
import java.util.LinkedList;

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

    private final LinkedList<byte[]> mWriteQueue = new LinkedList<>();

    private Listener mListener;

    DefaultSession(SelectionKey key) {
        mKey = key;
        mChannel = (SocketChannel) key.channel();
    }

    @Override
    public void enqueueWrite(byte[] data) throws IOException {
        synchronized (mWriteQueue) {
            if (!mKey.isValid()) {
                throw new IOException("SelectionKey is invalid");
            }
            mWriteQueue.addLast(data);
            if (mKey.interestOps() != (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
                SelectionKeyUtil.interestOps(mKey, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                mKey.selector().wakeup();
            }
        }
    }

    private int mOffset = 0;
    private byte[] mRemaining = null;

    @Override
    public void onFlushReady() throws IOException {
        byte[] data;

        synchronized (mWriteQueue) {
            while (mWriteBuffer.remaining() != 0 && (mRemaining != null || !mWriteQueue.isEmpty())) {
                if (mRemaining != null) {
                    data = mRemaining;
                    mRemaining = null;
                } else {
                    data = mWriteQueue.pollFirst();
                }

                if (data != null) {
                    int written = writeToBuffer(data, mOffset);
                    if (mOffset + written == data.length) {
                        mRemaining = null;
                        mOffset = 0;
                    } else {
                        mRemaining = data;
                        mOffset = mOffset + written;
                    }
                }
            }
        }

        boolean completed = flushBuffer();

        synchronized (mWriteQueue) {
            if (completed && mRemaining == null && mWriteQueue.isEmpty()) {
                SelectionKeyUtil.interestOps(mKey, SelectionKey.OP_READ);
            }
        }
    }

    private int writeToBuffer(byte[] data, int offset) throws IOException {
        int written = Math.min(data.length - offset, mWriteBuffer.capacity() - mWriteBuffer.position());
        mWriteBuffer.put(data, offset, written);
        return written;
    }

    private boolean flushBuffer() throws IOException {
        mWriteBuffer.flip();
        mChannel.write(mWriteBuffer);
        boolean completed = !mWriteBuffer.hasRemaining();
        mWriteBuffer.compact();
        return completed;
    }

    @Override
    public void onReadReady() throws IOException {
        LinkedList<byte[]> list = new LinkedList<>();

        while (true) {
            byte[] buff = read();
            if (buff == null) {
                break;
            }
            list.add(buff);
        }

        if (mListener != null) {
            mListener.onAppDataReceived(list);
        }
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
        mListener.onConnected();
    }

    private byte[] read() throws IOException {
        try {
            int length = mChannel.read(mReadBuffer);
            if (length == -1) {
                throw new IOException("EOF");
            } else if (length == 0) {
                return null;
            } else {
                mReadBuffer.flip();
                byte[] ret = new byte[length];
                mReadBuffer.get(ret);
                return ret;
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
