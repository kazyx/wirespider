package net.kazyx.apti;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;

class SelectionHandler {
    private static final String TAG = SelectionHandler.class.getSimpleName();

    private static final int BUFFER_SIZE = 4096;
    private final NonBlockingSocketConnection mSocketConnection;

    private SelectionKey mKey;

    private boolean mIsClosed = false;

    private final LinkedList<byte[]> mSendingQueue = new LinkedList<>();

    SelectionHandler(NonBlockingSocketConnection sc) {
        this.mSocketConnection = sc;
    }

    void onSelected(SelectionKey key) {
        Logger.d(TAG, "onSelected");
        mKey = key;
        try {
            if (!key.isValid()) {
                Logger.d(TAG, "Skip invalid key");
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

    private void onClosed() {
        Logger.d(TAG, "onClosed");
        close();
        mSocketConnection.onClosed();
    }

    private void onConnectReady() throws IOException {
        Logger.d(TAG, "onConnectReady");

        if (((SocketChannel) mKey.channel()).finishConnect()) {
            mKey.interestOps(SelectionKey.OP_READ);
            mSocketConnection.onConnected();
        } else {
            onClosed();
        }
    }

    private void onReadReady() throws IOException {
        Logger.d(TAG, "onReadReady");
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
        Logger.d(TAG, "writeAsync");
        if (mIsClosed) {
            Logger.d(TAG, "Quit writeAsync due to closed state");
            return;
        }
        synchronized (mSendingQueue) {
            mSendingQueue.addLast(data);
            if (mKey.interestOps() != (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
                mKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                if (!calledOnSelectorThread) {
                    mKey.selector().wakeup();
                }
            }
        }
    }

    void close() {
        Logger.d(TAG, "close");
        mIsClosed = true;
        synchronized (mSendingQueue) {
            mSendingQueue.clear();
            if (mKey != null) {
                mKey.cancel();
                IOUtil.close(mKey.channel());
            }
        }
    }

    private void onWriteReady() throws IOException {
        Logger.d(TAG, "onWriteReady");
        byte[] data;
        synchronized (mSendingQueue) {
            data = mSendingQueue.removeFirst();
        }
        ByteBuffer buff = ByteBuffer.wrap(data);
        SocketChannel ch = (SocketChannel) mKey.channel();
        int written = ch.write(buff);
        Logger.d(TAG, "Expected: " + data.length + ", Written: " + written);

        if (written != data.length) {
            mSendingQueue.addFirst(Arrays.copyOfRange(data, written, data.length));
        }

        synchronized (mSendingQueue) {
            if (mSendingQueue.size() == 0) {
                mKey.interestOps(SelectionKey.OP_READ);
            }
        }
    }
}