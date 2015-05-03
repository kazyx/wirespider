package net.kazyx.apti;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;

class SocketChannelProxy {
    private static final String TAG = SocketChannelProxy.class.getSimpleName();

    private static final int BUFFER_SIZE = 4096;
    private final NonBlockingSocketConnection mSocketConnection;

    private SelectionKey mKey;

    private boolean mIsClosed = false;

    private final LinkedList<byte[]> mWriteQueue = new LinkedList<>();

    SocketChannelProxy(NonBlockingSocketConnection sc) {
        this.mSocketConnection = sc;
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
            mSocketConnection.onClosed();
        }
    }

    void onCancelled() {
        AptiLog.d(TAG, "onCancelled");
        onClosed();
    }

    private void onClosed() {
        AptiLog.d(TAG, "onClosed");
        close();
        mSocketConnection.onClosed();
    }

    private void onConnectReady() throws IOException {
        // AptiLog.d(TAG, "onConnectReady");
        try {
            if (((SocketChannel) mKey.channel()).finishConnect()) {
                mKey.interestOps(SelectionKey.OP_READ);
                mSocketConnection.onConnected();
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
        LinkedList<ByteBuffer> list = new LinkedList<>();

        while (true) {
            ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
            int length = ch.read(buff);
            if (length == -1) {
                onClosed();
            } else if (length == 0) {
                break;
            } else {
                buff.flip();
                list.add(buff);
            }
        }

        if (list.size() != 0) {
            mSocketConnection.onDataReceived(list);
        }
    }

    void writeAsync(byte[] data) {
        writeAsync(data, false);
    }

    void writeAsync(byte[] data, boolean calledOnSelectorThread) {
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
}
