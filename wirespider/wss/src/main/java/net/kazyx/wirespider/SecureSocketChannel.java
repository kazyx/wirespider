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
import net.kazyx.wirespider.util.SelectionKeyUtil;
import net.kazyx.wirespider.util.WsLog;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

class SecureSocketChannel implements Closeable {
    private static final String TAG = SecureSocketChannel.class.getSimpleName();

    private final SelectionKey mKey;
    private final SocketChannel mChannel;
    private final SSLEngine mSslEngine;

    private ByteBuffer mNetIn;
    private ByteBuffer mNetOut;
    private static final Object mOutSync = new Object();

    private ByteBuffer mAppIn;
    private final ByteBuffer mAppOut;

    SecureSocketChannel(SelectionKey key, SSLEngine sslEngine, int appOutBufferSize) {
        mKey = key;
        mChannel = (SocketChannel) key.channel();
        mSslEngine = sslEngine;

        SSLSession sslSession = sslEngine.getSession();

        int packetBufferSize = sslSession.getPacketBufferSize();
        mNetOut = ByteBuffer.allocateDirect(packetBufferSize);
        mNetIn = ByteBuffer.allocateDirect(packetBufferSize);

        mAppOut = ByteBuffer.allocateDirect(appOutBufferSize);
        mAppIn = ByteBuffer.allocateDirect(sslSession.getApplicationBufferSize());
    }

    void init() throws IOException {
        mSslEngine.beginHandshake();
        evaluateCurrentStatus();
    }

    void wrapAndEnqueue(ByteBuffer src) throws IOException {
        // WsLog.v(TAG, "Wrap and Enqueue");
        synchronized (mOutSync) {
            while (src.remaining() != 0) {
                if (mAppOut.remaining() < src.remaining()) {
                    byte[] tmp = new byte[mAppOut.remaining()];
                    src.get(tmp);
                    mAppOut.put(tmp);
                } else {
                    mAppOut.put(src);
                }

                mAppOut.flip();
                wrap();
                mAppOut.compact();
            }
        }
    }

    void onReadReady() throws IOException {
        // WsLog.v(TAG, "onReadReady");
        unwrap();
    }

    private Session.Listener mListener;

    void setDataListener(Session.Listener listener) {
        mListener = listener;
    }

    private void onUnwrapped() {
        // WsLog.v(TAG, "onUnwrapped");
        if (mListener == null) {
            return;
        }

        mAppIn.flip();
        if (mAppIn.limit() == 0) {
            mAppIn.clear();
            return;
        }

        ByteBuffer ret = ByteBuffer.allocate(mAppIn.limit());
        ret.put(mAppIn);
        mAppIn.clear();
        ret.flip();

        // WsLog.v(TAG, "Unwrapped", ret);
        mListener.onAppDataReceived(ret);
    }

    private void evaluateCurrentStatus() throws IOException {
        evaluateStatus(mSslEngine.getHandshakeStatus());
    }

    private void evaluateStatus(SSLEngineResult.HandshakeStatus hsStatus) throws IOException {
        // WsLog.v(TAG, "evaluateStatus", hsStatus.name());

        switch (hsStatus) {
            case NEED_TASK:
                Runnable task;
                while ((task = mSslEngine.getDelegatedTask()) != null) {
                    task.run();
                }
                evaluateCurrentStatus();
                break;
            case NEED_WRAP:
                synchronized (mOutSync) {
                    mAppOut.flip();
                    wrap();
                    mAppOut.compact();
                }
                break;
            case NEED_UNWRAP:
                unwrap();
                break;
            case FINISHED:
                WsLog.d(TAG, "SSL handshake completed: ", mSslEngine.getSession().getProtocol());
                if (mListener != null) {
                    mListener.onConnected();
                }
                return;
            default:
                break;
        }
    }

    private void wrap() throws IOException {
        SSLEngineResult result = mSslEngine.wrap(mAppOut, mNetOut);
        // WsLog.v(TAG, "wrap: ", result.toString());

        final SSLEngineResult.Status status = result.getStatus();
        switch (status) {
            case OK:
                if (mKey.interestOps() != (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
                    SelectionKeyUtil.interestOps(mKey, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    mKey.selector().wakeup();
                }
                break;
            case BUFFER_OVERFLOW:
                mNetOut = reallocateByOverflow(mNetOut, mSslEngine.getSession().getPacketBufferSize());
                wrap();
                break;
            case CLOSED:
                WsLog.d(TAG, "SSLEngine wrap result: CLOSED");
                close();
                break;
            default:
                break;
        }
    }

    void flush() throws IOException {
        // WsLog.d(TAG, "flush");
        synchronized (mOutSync) {
            mNetOut.flip();
            mChannel.write(mNetOut);
            mNetOut.compact();
            if (mNetOut.position() == 0) {
                SelectionKeyUtil.interestOps(mKey, SelectionKey.OP_READ);
            }
        }

        evaluateCurrentStatus();
    }

    private void unwrap() throws IOException {
        if (mNetIn.position() == 0) {
            final int count = mChannel.read(mNetIn);
            if (count == -1) {
                close();
                throw new IOException("Detected end of stream");
            }
        }

        mNetIn.flip();
        SSLEngineResult result = mSslEngine.unwrap(mNetIn, mAppIn);
        mNetIn.compact();
        // WsLog.d(TAG, "unwrap: ", result.toString());

        final SSLEngineResult.Status status = result.getStatus();
        switch (status) {
            case OK:
                onUnwrapped();
                evaluateStatus(result.getHandshakeStatus());
                break;
            case BUFFER_UNDERFLOW:
                mNetIn = reallocateByUnderflow(mNetIn, mAppIn, mSslEngine.getSession().getPacketBufferSize());
                break;
            case BUFFER_OVERFLOW:
                mAppIn = reallocateByOverflow(mAppIn, mSslEngine.getSession().getApplicationBufferSize());
                unwrap();
                break;
            case CLOSED:
                WsLog.d(TAG, "SSLEngine unwrap result: CLOSED");
                close();
                break;
            default:
                break;
        }
    }

    private static ByteBuffer reallocateByOverflow(ByteBuffer dst, int newSize) {
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(dst.position() + newSize);
        dst.flip();
        newBuffer.put(dst);
        // WsLog.d(TAG, "Original: " + dst.capacity() + ", New: " + newBuffer.capacity());
        return newBuffer;
    }

    private static ByteBuffer reallocateByUnderflow(ByteBuffer src, ByteBuffer dst, int newSize) {
        if (newSize > dst.capacity()) {
            ByteBuffer newBuffer = ByteBuffer.allocateDirect(newSize);
            src.flip();
            newBuffer.put(src);
            return newBuffer;
        }
        return src;
    }

    @Override
    public void close() {
        WsLog.d(TAG, "close");
        try {
            mSslEngine.closeInbound();
        } catch (SSLException e) {
            // WsLog.d(TAG, "Failed to close InBound");
        }
        mSslEngine.closeOutbound();
        if (mChannel.isOpen()) {
            IOUtil.close(mChannel);
        }
    }
}
