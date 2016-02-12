/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.Extension;
import net.kazyx.wirespider.util.ArgumentCheck;
import net.kazyx.wirespider.util.IOUtil;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * Generic WebSocket connection.
 */
public abstract class WebSocket {
    private static final String TAG = WebSocket.class.getSimpleName();

    private final SocketEngine mEngine;

    SocketEngine socketEngine() {
        return mEngine;
    }

    private final URI mURI;

    final URI remoteUri() {
        return mURI;
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

    private final WebSocketHandler mCallbackHandler;

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

    final SocketChannelProxy socketChannelProxy() {
        return mSocketChannelProxy;
    }

    private final FrameTx mFrameTx;
    private final FrameRx mFrameRx;
    private final Handshake mHandshake;

    final Handshake handshake() {
        return mHandshake;
    }

    WebSocket(SessionRequest req, SocketEngine engine, SocketChannel ch) {
        mURI = req.uri();
        mCallbackHandler = req.handler();
        mMaxResponsePayloadSize = req.maxResponsePayloadSizeInBytes();
        mEngine = engine;
        mSocketChannel = ch;

        mSocketChannelProxy = new SocketChannelProxy(mChannelProxyListener);

        mFrameTx = newFrameTx();
        mFrameRx = newFrameRx(mRxListener);
        mHandshake = newHandshake();
        mHandshake.responseHandler(req.handshakeHandler());
    }

    abstract FrameTx newFrameTx();

    abstract FrameRx newFrameRx(FrameRx.Listener listener);

    abstract Handshake newHandshake();

    abstract void onSocketConnected();

    abstract void onHandshakeFailed();

    abstract void onHandshakeCompleted();

    /**
     * @return Active WebSocket extensions on this session.
     */
    public List<Extension> extensions() {
        return mHandshake.extensions();
    }

    /**
     * @return Active protocol of this session, or {@code null} if no protocol is defined.
     */
    public String protocol() {
        return mHandshake.protocol();
    }

    /**
     * Send text message asynchronously.
     *
     * @param message Text message to send.
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
     */
    public void sendBinaryMessageAsync(byte[] message) {
        ArgumentCheck.rejectNull(message);
        if (!isConnected()) {
            return;
        }

        mFrameTx.sendBinaryAsync(message);
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
     * Close TCP connection without WebSocket closing handshake.
     */
    public void closeNow() {
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
                mCallbackHandler.onClosed(code, reason);
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
                onHandshakeFailed();
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

                    for (Extension ext : extensions) {
                        WsLog.d(TAG, "Extension accepted: " + ext.name());
                        mFrameTx.setPayloadFilter(ext.filter());
                        mFrameRx.setPayloadFilter(ext.filter());
                        // TODO multiple extensions
                    }

                    onHandshakeCompleted();

                    if (data.remaining() != 0) {
                        mFrameRx.onDataReceived(data);
                    }
                } catch (BufferUnsatisfiedException e) {
                    // wait for the next data.
                } catch (HandshakeFailureException e) {
                    WsLog.d(TAG, "HandshakeFailureException: " + e.getMessage());
                    onHandshakeFailed();
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
            if (!isConnected()) {
                return;
            }
            WsLog.d(TAG, "onPongFrame", message);
            mCallbackHandler.onPong(message);
        }

        @Override
        public void onCloseFrame(int code, String reason) {
            if (!isConnected()) {
                return;
            }
            WsLog.d(TAG, "onCloseFrame", code + " " + reason);
            if (code != CloseStatusCode.ABNORMAL_CLOSURE.statusCode) {
                sendCloseFrame(CloseStatusCode.NORMAL_CLOSURE, "Close frame response", false);
            }
            // TODO Wait for a minute to send close frame?
            closeAndRaiseEvent(code, reason);
        }

        @Override
        public void onBinaryMessage(ByteBuffer message) {
            if (!isConnected()) {
                return;
            }
            mCallbackHandler.onBinaryMessage(message.array());
        }

        @Override
        public void onTextMessage(String message) {
            if (!isConnected()) {
                return;
            }
            mCallbackHandler.onTextMessage(message);
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
