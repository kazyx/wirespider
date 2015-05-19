package net.kazyx.apti;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;

class SocketChannelProxy implements SocketChannelWriter {
    private static final String TAG = SocketChannelProxy.class.getSimpleName();

    private final AsyncSource mAsync;
    private final Listener mListener;

    private SelectionKey mKey;

    private boolean mIsClosed = false;

    private final LinkedList<byte[]> mWriteQueue = new LinkedList<>();

    SocketChannelProxy(AsyncSource async, Listener listener) {
        mAsync = async;
        this.mListener = listener;
    }

    void onSelected(SelectionKey key) {
        mKey = key;
        try {
            if (!key.isValid()) {
                AptiLog.d(TAG, "Skip invalid key");
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
        AptiLog.d(TAG, "onCancelled");
        onClosed();
    }

    private void onClosed() {
        AptiLog.d(TAG, "onClosed");
        close();
        mListener.onClosed();
    }

    private void onConnectReady() throws IOException {
        // AptiLog.d(TAG, "onConnectReady");
        try {
            if (((SocketChannel) mKey.channel()).finishConnect()) {
                mKey.interestOps(SelectionKey.OP_READ);
                mListener.onSocketConnected();
                return;
            }
        } catch (CancelledKeyException e) {
            // Connected but SelectionKey is cancelled. Fall through to failure
        }
        AptiLog.d(TAG, "Failed to connect");
        onClosed();
    }

    private void onReadReady() throws IOException {
        // AptiLog.d(TAG, "onReadReady");
        SocketChannel ch = (SocketChannel) mKey.channel();
        LinkedList<byte[]> list = new LinkedList<>();


        while (true) {
            byte[] buff = mAsync.read(ch);
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
        // AptiLog.d(TAG, "writeAsync");
        if (mIsClosed) {
            AptiLog.d(TAG, "Quit writeAsync due to closed state");
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
        // AptiLog.d(TAG, "onWriteReady");
        byte[] data;
        synchronized (mWriteQueue) {
            data = mWriteQueue.removeFirst();
        }
        ByteBuffer buff = ByteBuffer.wrap(data);
        SocketChannel ch = (SocketChannel) mKey.channel();
        int written = ch.write(buff);
        // AptiLog.d(TAG, "Expected: " + data.length + ", Written: " + written);

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
