package net.kazyx.apti;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedList;

class SelectionHandler {
    private static final int BUFFER_SIZE = 4096;
    private final URI mURI;
    private final NonBlockingSocketConnection mSocketConnection;

    private SelectionKey mKey;

    private final LinkedList<byte[]> mSendingQueue = new LinkedList<>();

    SelectionHandler(URI remoteURI, NonBlockingSocketConnection sc) {
        this.mURI = remoteURI;
        this.mSocketConnection = sc;
    }

    void onSelected(SelectionKey key) {
        mKey = key;
        try {
            if (key.isConnectable()) {
                connect();
            }
            if (key.isReadable()) {
                read();
            }
            if (key.isWritable()) {
                write();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onClosed() {
        close();
        mSocketConnection.onClosed();
    }

    private void connect() throws IOException {
        SocketChannel channel = (SocketChannel) mKey.channel();
        channel.configureBlocking(false);

        final Socket socket = channel.socket();
        socket.setTcpNoDelay(true);
        // bind local address here.

        socket.connect(new InetSocketAddress(mURI.getHost(), (mURI.getPort() != -1) ? mURI.getPort() : 80));
        mKey.interestOps(SelectionKey.OP_READ);

        mSocketConnection.onConnected(this);
    }

    private void read() throws IOException {
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
        synchronized (mSendingQueue) {
            mSendingQueue.addLast(data);
            mKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }

    void close() {
        mSendingQueue.clear();
        mKey.cancel();
        IOUtil.close(mKey.channel());
    }

    private void write() throws IOException {
        byte[] data;
        synchronized (mSendingQueue) {
            data = mSendingQueue.getFirst();
        }
        ByteBuffer buff = ByteBuffer.wrap(data);
        SocketChannel ch = (SocketChannel) mKey.channel();
        int written = ch.write(buff);
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
