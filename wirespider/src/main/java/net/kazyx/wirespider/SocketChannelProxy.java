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
import java.util.Arrays;
import java.util.LinkedList;

class SocketChannelProxy implements SocketChannelWriter {
    private static final String TAG = SocketChannelProxy.class.getSimpleName();

    private final SocketEngine mEngine;
    private final Listener mListener;

    private SelectionKey mKey;

    private boolean mIsClosed = false;

    private final LinkedList<byte[]> mWriteQueue = new LinkedList<>();

    SocketChannelProxy(SocketEngine engine, Listener listener) {
        mEngine = engine;
        this.mListener = listener;
    }

    void onSelected(SelectionKey key) {
        mKey = key;
        try {
            if (!key.isValid()) {
                Log.d(TAG, "Skip invalid key");
                return;
            }
            if (key.isConnectable()) {
                onConnectReady();
            }
            if (key.isReadable()) {
                onReadReady();
            }
            if (key.isWritable() && key.isValid()) {
                onWriteReady();
            }
        } catch (IOException | CancelledKeyException e) {
            onClosed();
        }
    }

    void onCancelled() {
        Log.d(TAG, "onCancelled");
        onClosed();
    }

    private void onClosed() {
        Log.d(TAG, "onClosed");
        close();
        mListener.onClosed();
    }

    private void onConnectReady() throws IOException {
        // Log.d(TAG, "onConnectReady");
        try {
            if (((SocketChannel) mKey.channel()).finishConnect()) {
                mKey.interestOps(SelectionKey.OP_READ);
                mListener.onSocketConnected();
                return;
            }
        } catch (CancelledKeyException e) {
            // Connected but SelectionKey is cancelled. Fall through to failure
        }
        Log.d(TAG, "Failed to connect");
        onClosed();
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
            Log.d(TAG, "Quit writeAsync due to closed state");
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

    private void onWriteReady() throws IOException {
        // Log.d(TAG, "onWriteReady");
        byte[] data;
        synchronized (mWriteQueue) {
            data = mWriteQueue.removeFirst();
        }
        ByteBuffer buff = ByteBuffer.wrap(data);
        SocketChannel ch = (SocketChannel) mKey.channel();
        int written = ch.write(buff);
        // Log.d(TAG, "Expected: " + data.length + ", Written: " + written);

        if (written != data.length) {
            mWriteQueue.addFirst(Arrays.copyOfRange(data, written, data.length));
        }

        synchronized (mWriteQueue) {
            try {
                if (mWriteQueue.size() == 0) {
                    mKey.interestOps(SelectionKey.OP_READ);
                }
            } catch (CancelledKeyException e) {
                onClosed();
            }
        }
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
