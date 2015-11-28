/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.IOUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

class SocketChannelProxy implements SocketChannelWriter {
    private static final String TAG = SocketChannelProxy.class.getSimpleName();

    private static final int WRITE_BUFFER_SIZE = 1024;
    private final ByteBuffer mWriteBuffer = ByteBuffer.allocateDirect(WRITE_BUFFER_SIZE);

    private final SocketEngine mEngine;
    private final Listener mListener;

    private SelectionKey mKey;

    private boolean mIsClosed = false;

    private final LinkedList<byte[]> mWriteQueue = new LinkedList<>();

    SocketChannelProxy(SocketEngine engine, Listener listener) {
        mEngine = engine;
        this.mListener = listener;
    }

    /**
     * Called only when the connection is already established.
     *
     * @param key Key of the selected channel.
     */
    void onSelected(SelectionKey key) {
        try {
            if (!key.isValid()) {
                WsLog.d(TAG, "Skip invalid key");
                return;
            }
            if (key.isReadable()) {
                onReadReady();
            }
            if (key.isWritable()) {
                onWriteReady();
            }
        } catch (IOException | CancelledKeyException e) {
            onClosed();
        }
    }

    void onConnected(SelectionKey key) {
        mKey = key;
        mListener.onSocketConnected();
    }

    void onConnectionFailed() {
        WsLog.d(TAG, "Failed to connect");
        onClosed();
    }

    void onCancelled() {
        WsLog.d(TAG, "onCancelled");
        onClosed();
    }

    private void onClosed() {
        WsLog.d(TAG, "onClosed");
        close();
        mListener.onClosed();
    }

    private void onReadReady() throws IOException {
        // Log.d(TAG, "onReadReady");
        SocketChannel ch = (SocketChannel) mKey.channel();
        LinkedList<byte[]> list = new LinkedList<>();

        while (true) {
            byte[] buff = mEngine.read(ch);
            if (buff == null) {
                break;
            }
            list.add(buff);
        }

        if (list.size() != 0) {
            mListener.onDataReceived(list);
        }
    }

    @Override
    public void writeAsync(byte[] data) {
        writeAsync(data, false);
    }

    @Override
    public void writeAsync(byte[] data, boolean calledOnSelectorThread) {
        // Log.d(TAG, "writeAsync");
        if (mIsClosed) {
            WsLog.d(TAG, "Quit writeAsync due to closed state");
            return;
        }
        synchronized (mWriteQueue) {
            if (!mKey.isValid()) {
                onClosed();
                return;
            }
            try {
                mWriteQueue.addLast(data);
                if (mKey.interestOps() != (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
                    mKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    if (!calledOnSelectorThread) {
                        mKey.selector().wakeup();
                    }
                }
            } catch (CancelledKeyException e) {
                onClosed();
            }
        }
    }

    void close() {
        mIsClosed = true;
        synchronized (mWriteQueue) {
            mWriteQueue.clear();
            if (mKey != null) {
                mKey.cancel();
                IOUtil.close(mKey.channel());
            }
        }
    }

    private int mOffset = 0;
    private byte[] mRemaining = null;

    private void onWriteReady() throws IOException {
        byte[] data;
        if (mRemaining != null) {
            data = mRemaining;
            mRemaining = null;
        } else {
            synchronized (mWriteQueue) {
                data = mWriteQueue.pollFirst();
            }
        }

        if (data != null) {
            int written = writeBuffer(data, mOffset);
            if (mOffset + written == data.length) {
                mRemaining = null;
                mOffset = 0;
            } else {
                mRemaining = data;
                mOffset = mOffset + written;
            }
        }

        boolean completed = flushBuffer();

        synchronized (mWriteQueue) {
            try {
                if (completed && mRemaining == null && mWriteQueue.isEmpty()) {
                    mKey.interestOps(SelectionKey.OP_READ);
                }
            } catch (CancelledKeyException e) {
                onClosed();
            }
        }
    }

    private boolean flushBuffer() throws IOException {
        mWriteBuffer.flip();
        mEngine.write((SocketChannel) mKey.channel(), mWriteBuffer);
        boolean completed = !mWriteBuffer.hasRemaining();
        mWriteBuffer.compact();
        return completed;
    }

    private int writeBuffer(byte[] data, int offset) throws IOException {
        int written = Math.min(data.length - offset, mWriteBuffer.capacity() - mWriteBuffer.position());
        mWriteBuffer.put(data, offset, written);
        return written;
    }

    interface Listener {
        /**
         * Called when the Socket is connected.
         */
        void onSocketConnected();

        /**
         * Called when the Socket or SocketChannel is closed.
         */
        void onClosed();

        /**
         * Called when the data is received from the Socket.
         *
         * @param data Received data.
         */
        void onDataReceived(LinkedList<byte[]> data);
    }
}
