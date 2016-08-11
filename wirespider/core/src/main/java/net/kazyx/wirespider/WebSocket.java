/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.exception.HandshakeFailureException;
import net.kazyx.wirespider.exception.PayloadUnderflowException;
import net.kazyx.wirespider.extension.Extension;
import net.kazyx.wirespider.util.ArgumentCheck;
import net.kazyx.wirespider.util.IOUtil;
import net.kazyx.wirespider.util.WsLog;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * Generic WebSocket connection.
 */
public abstract class WebSocket implements Closeable {
    private static final String TAG = WebSocket.class.getSimpleName();

    static final String WSS_SCHEME = "wss";
    static final int DEFAULT_WS_PORT = 80;
    static final int DEFAULT_WSS_PORT = 443;

    private final SelectorLoop mLoop;

    final SelectorLoop selectorLoop() {
        return mLoop;
    }

    private final SessionRequest mReq;

    public final URI remoteUri() {
        return mReq.uri();
    }

    private final SocketChannel mSocketChannel;

    final SocketChannel socketChannel() {
        return mSocketChannel;
    }

    private final int mMaxResponsePayloadSize;

    /**
     * @return Maximum size of response payload to accept.
     * @see SessionRequest.Builder#setMaxResponsePayloadSizeInBytes(int)
     */
    public int maxResponsePayloadSizeInBytes() {
        return mMaxResponsePayloadSize;
    }

    private final Object mCloseCallbackLock = new Object();

    private boolean mIsHandshakeCompleted = false;

    private boolean mIsConnected = false;

    /**
     * @return WebSocket connection is established or not.
     */
    public boolean isConnected() {
        return mIsConnected;
    }

    private final SocketChannelProxy mSocketChannelProxy;

    protected final SocketChannelProxy socketChannelProxy() {
        return mSocketChannelProxy;
    }

    private final FrameTx mFrameTx;
    private final FrameRx mFrameRx;
    private final Handshake mHandshake;

    final Handshake handshake() {
        return mHandshake;
    }

    WebSocket(SessionRequest req, SelectorLoop loop, SocketChannel ch) {
        mReq = req;
        mMaxResponsePayloadSize = req.maxResponsePayloadSizeInBytes();
        mLoop = loop;
        mSocketChannel = ch;

        mSocketChannelProxy = new SocketChannelProxy(mChannelProxyListener);

        mFrameTx = newFrameTx();
        mFrameRx = newFrameRx(mRxListener);
        mHandshake = newHandshake();
        mHandshake.responseHandler(req.handshakeHandler());
    }

    protected abstract FrameTx newFrameTx();

    protected abstract FrameRx newFrameRx(FrameRx.Listener listener);

    protected abstract Handshake newHandshake();

    abstract void onSocketConnected();

    abstract void onHandshakeFailed(IOException e);

    abstract void onHandshakeCompleted();

    /**
     * @return Active WebSocket extensions on this session.
     */
    public List<Extension> extensions() {
        return mHandshake.extensions();
    }

    /**
     * @return Active sub-protocol of this session, or {@code null} if no protocol is defined.
     */
    public String protocol() {
        return mHandshake.protocol();
    }

    /**
     * Send text message asynchronously.
     *
     * @param message Text message to send.
     * @throws IllegalStateException {@link PartialMessageWriter} derived from this {@link WebSocket} is holding lock.
     */
    public void sendTextMessageAsync(String message) {
        ArgumentCheck.rejectNull(message);
        if (!isConnected()) {
            return;
        }

        mFrameTx.sendTextAsync(message);
    }

    /**
     * Send binary message asynchronously.<br>
     * Note that byte array argument might be changed to the masked data in case of client side.
     *
     * @param message Binary message to send.
     * @throws IllegalStateException {@link PartialMessageWriter} derived from this {@link WebSocket} is holding lock.
     */
    public void sendBinaryMessageAsync(byte[] message) {
        ArgumentCheck.rejectNull(message);
        if (!isConnected()) {
            return;
        }

        mFrameTx.sendBinaryAsync(message);
    }

    /**
     * Partial message writer is holding lock for other data frame operations.
     * <p>
     * <b>Note: {@link PartialMessageWriter} is designed to be used only on the thread which crated the instance.</b>
     * </p>
     * <p>
     * While {@link PartialMessageWriter} is holding a lock, {@link #sendTextMessageAsync(String)} and {@link #sendBinaryMessageAsync(byte[])} throw {@link IllegalStateException}.<br>
     * While another {@link PartialMessageWriter} derived from this {@link WebSocket} is holding lock, this method throws {@link IllegalStateException}.<br>
     * The lock is cleared by calling {@link PartialMessageWriter#close()}.
     * </p>
     *
     * @return Newly created writer to send partial message frames.
     * @throws IllegalStateException Another {@link PartialMessageWriter} derived from this {@link WebSocket} is holding lock.
     */
    public PartialMessageWriter newPartialMessageWriter() {
        return new PartialMessageWriter(mFrameTx);
    }

    /**
     * Send ping frame asynchronously.
     *
     * @param message Ping message to send.
     */
    public void sendPingAsync(String message) {
        ArgumentCheck.rejectNull(message);
        if (!isConnected()) {
            return;
        }

        mFrameTx.sendPingAsync(message);
    }

    /**
     * Close WebSocket connection gracefully.<br>
     * If it is already closed, nothing happens.<br>
     * <br>
     * Equivalent to {@link #closeAsync(CloseStatusCode, String)} with {@link CloseStatusCode#NORMAL_CLOSURE}.
     */
    public void closeAsync() {
        closeAsync(CloseStatusCode.NORMAL_CLOSURE, "normal closure");
    }

    /**
     * Close WebSocket connection gracefully.<br>
     * If it is already closed, nothing happens.
     *
     * @param code Close status code to send.
     * @param reason Close reason phrase to send.
     */
    public void closeAsync(final CloseStatusCode code, final String reason) {
        ArgumentCheck.rejectNullArgs(code, reason);
        if (!isConnected()) {
            return;
        }

        sendCloseFrame(code, reason, true);
    }

    private void sendCloseFrame(CloseStatusCode code, String reason, final boolean waitForResponse) {
        mFrameTx.sendCloseAsync(code, reason);

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (waitForResponse) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        WsLog.e(TAG, "Close frame response waiter interrupted");
                    }
                }
                closeAndRaiseEvent(CloseStatusCode.NORMAL_CLOSURE, "Normal closure");
            }
        }).start();
    }

    /**
     * Close TCP connection without close handshake.
     */
    @Override
    public void close() {
        closeAndRaiseEvent(CloseStatusCode.NORMAL_CLOSURE, "Normal closure");
    }

    private void closeAndRaiseEvent(CloseStatusCode status, String message) {
        closeAndRaiseEvent(status.asNumber(), message);
    }

    private void closeAndRaiseEvent(int status, String message) {
        if (!isConnected()) {
            return;
        }

        mSocketChannelProxy.close();
        IOUtil.close(mSocketChannel);
        invokeOnClosed(status, message);
    }

    private void invokeOnClosed(int code, String reason) {
        synchronized (mCloseCallbackLock) {
            if (isConnected()) {
                WsLog.d(TAG, "Invoke onClosed", code);
                mIsConnected = false;
                if (mReq.mCloseHandler != null) {
                    mReq.mCloseHandler.onClosed(code, reason);
                }
            }
        }
    }

    private SocketChannelProxy.Listener mChannelProxyListener = new SocketChannelProxy.Listener() {
        @Override
        public void onSocketConnected() {
            WebSocket.this.onSocketConnected();
        }

        @Override
        public void onClosed() {
            if (mIsHandshakeCompleted) {
                WsLog.d(TAG, "Socket error detected");
                closeAndRaiseEvent(CloseStatusCode.ABNORMAL_CLOSURE, "Socket error detected");
            } else {
                WsLog.d(TAG, "Socket error detected while opening handshake");
                onHandshakeFailed(new IOException("Socket connection error while opening handshake"));
            }
        }

        @Override
        public void onDataReceived(final ByteBuffer data) {
            // Log.d(TAG, "SocketChannelProxy onDataReceived");
            if (!isConnected()) {
                try {
                    mHandshake.onHandshakeResponse(data);
                    mIsHandshakeCompleted = true;
                    mIsConnected = true;
                    List<Extension> extensions = mHandshake.extensions();

                    mFrameTx.setExtensions(extensions);
                    mFrameRx.setExtensions(extensions);

                    onHandshakeCompleted();

                    if (data.remaining() != 0) {
                        mFrameRx.onDataReceived(data);
                    }
                } catch (PayloadUnderflowException e) {
                    // wait for the next data.
                } catch (HandshakeFailureException e) {
                    WsLog.d(TAG, "HandshakeFailureException: " + e.getMessage());
                    onHandshakeFailed(e);
                }
            } else {
                mFrameRx.onDataReceived(data);
            }
        }
    };

    private FrameRx.Listener mRxListener = new FrameRx.Listener() {
        @Override
        public void onPingFrame(String message) {
            if (!isConnected()) {
                return;
            }
            WsLog.d(TAG, "onPingFrame", message);
            mFrameTx.sendPongAsync(message);
        }

        @Override
        public void onPongFrame(String message) {
            WsLog.d(TAG, "onPongFrame", message);
            if (!isConnected() || mReq.mPongHandler == null) {
                return;
            }
            mReq.mPongHandler.onPong(message);
        }

        @Override
        public void onCloseFrame(int code, String reason) {
            WsLog.d(TAG, "onCloseFrame", code + " " + reason);
            if (!isConnected()) {
                return;
            }
            if (code != CloseStatusCode.ABNORMAL_CLOSURE.statusCode) {
                sendCloseFrame(CloseStatusCode.NORMAL_CLOSURE, "Close frame response", false);
            }
            // TODO Wait for a minute to send close frame?
            closeAndRaiseEvent(code, reason);
        }

        @Override
        public void onInvalidPayloadError(IOException e) {
            if (!isConnected()) {
                return;
            }
            WsLog.d(TAG, "Received invalid payload");
            sendCloseFrame(CloseStatusCode.INVALID_FRAME_PAYLOAD_DATA, "Invalid payload", false);

            // TODO Wait for a minute to send close frame?
            closeAndRaiseEvent(CloseStatusCode.INVALID_FRAME_PAYLOAD_DATA, "Invalid payload");
        }

        @Override
        public void onBinaryMessage(ByteBuffer message) {
            if (!isConnected() || mReq.mBinaryHandler == null) {
                return;
            }
            mReq.mBinaryHandler.onBinaryMessage(message.array());
        }

        @Override
        public void onTextMessage(String message) {
            if (!isConnected() || mReq.mTextHandler == null) {
                return;
            }
            mReq.mTextHandler.onTextMessage(message);
        }

        @Override
        public void onProtocolViolation() {
            WsLog.d(TAG, "Protocol violation detected");
            // TODO send error code to remote?
            closeAndRaiseEvent(CloseStatusCode.PROTOCOL_ERROR, "Protocol violation detected");
        }

        @Override
        public void onPayloadOverflow() {
            WsLog.d(TAG, "Response payload size overflow");
            // TODO send error code to remote?
            closeAndRaiseEvent(CloseStatusCode.MESSAGE_TOO_BIG, "Response payload size overflow");
        }
    };
}
