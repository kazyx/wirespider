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
import java.util.LinkedList;

class SocketChannelProxy implements SocketChannelWriter {
    private static final String TAG = SocketChannelProxy.class.getSimpleName();

    private Session mSession;
    private final Listener mListener;

    private boolean mIsClosed = false;

    SocketChannelProxy(Listener listener) {
        mListener = listener;
    }

    void onConnected(Session session) {
        mSession = session;
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

    void onClosed() {
        WsLog.d(TAG, "onClosed");
        close();
        mListener.onClosed();
    }

    void onReceived(LinkedList<byte[]> data) {
        if (data.size() != 0) {
            mListener.onDataReceived(data);
        }
    }

    @Override
    public void writeAsync(byte[] data) {
        writeAsync(data, false);
    }

    @Override
    public void writeAsync(byte[] data, boolean calledOnSelectorThread) {
        // Log.d(TAG, "writeAsync");
        if (mIsClosed || mSession == null) {
            WsLog.d(TAG, "Quit writeAsync due to closed state");
            return;
        }
        try {
            mSession.enqueueWrite(data);
        } catch (IOException e) {
            IOUtil.close(mSession);
            onClosed();
        }
    }

    void close() {
        mIsClosed = true;
        IOUtil.close(mSession);
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
