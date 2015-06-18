package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.Extension;
import net.kazyx.wirespider.extension.compression.PerMessageCompression;

import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * Generic WebSocket connection.
 */
public abstract class WebSocket {
    private static final String TAG = WebSocket.class.getSimpleName();

    private final AsyncSource mAsync;

    final AsyncSource asyncSource() {
        return mAsync;
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
     * @see WebSocketSeed.Builder#maxResponsePayloadSizeInBytes(int)
     */
    public int maxResponsePayloadSizeInBytes() {
        return mMaxResponsePayloadSize;
    }

    private final InterpretedEventHandler mCallbackHandler;

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

    WebSocket(WebSocketSeed seed, AsyncSource async, SocketChannel ch) {
        mURI = seed.uri();
        mCallbackHandler = seed.handler();
        mMaxResponsePayloadSize = seed.maxResponsePayloadSizeInBytes();
        mAsync = async;
        mSocketChannel = ch;

        mSocketChannelProxy = new SocketChannelProxy(mAsync, mChannelProxyListener);

        mFrameTx = newFrameTx();
        mFrameRx = newFrameRx(mRxListener);
        mHandshake = newHandshake();
        mHandshake.responseHandler(seed.handshakeHandler());
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

        mAsync.safeAsync(new Runnable() {
            @Override
            public void run() {
                if (waitForResponse) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Close frame response waiter interrupted");
                    }
                }
                closeAndRaiseEvent(CloseStatusCode.NORMAL_CLOSURE, "Normal closure");
            }
        });
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
                Log.d(TAG, "Invoke onClosed", code);
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
                Log.d(TAG, "Socket error detected");
                closeAndRaiseEvent(CloseStatusCode.ABNORMAL_CLOSURE, "Socket error detected");
            } else {
                Log.d(TAG, "Socket error detected while opening handshake");
                onHandshakeFailed();
            }
        }

        @Override
        public void onDataReceived(final LinkedList<byte[]> data) {
            // Log.d(TAG, "SocketChannelProxy onDataReceived");
            if (!isConnected()) {
                try {
                    LinkedList<byte[]> remaining = mHandshake.onHandshakeResponse(data);
                    mIsHandshakeCompleted = true;
                    mIsConnected = true;
                    List<Extension> extensions = mHandshake.extensions();

                    for (Extension ext : extensions) {
                        Log.d(TAG, "Extension accepted: " + ext.name());
                        if (ext instanceof PerMessageCompression) {
                            mFrameTx.compressMessagesWith((PerMessageCompression) ext);
                            mFrameRx.decompressMessagesWith((PerMessageCompression) ext);
                        }
                    }

                    onHandshakeCompleted();

                    if (remaining.size() != 0) {
                        mFrameRx.onDataReceived(remaining);
                    }
                } catch (BufferUnsatisfiedException e) {
                    // wait for the next data.
                } catch (HandshakeFailureException e) {
                    Log.d(TAG, "HandshakeFailureException: " + e.getMessage());
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
            Log.d(TAG, "onPingFrame", message);
            mFrameTx.sendPongAsync(message);
        }

        @Override
        public void onPongFrame(String message) {
            if (!isConnected()) {
                return;
            }
            Log.d(TAG, "onPongFrame", message);
            mCallbackHandler.onPong(message);
        }

        @Override
        public void onCloseFrame(int code, String reason) {
            if (!isConnected()) {
                return;
            }
            Log.d(TAG, "onCloseFrame", code + " " + reason);
            if (code != CloseStatusCode.ABNORMAL_CLOSURE.statusCode) {
                sendCloseFrame(CloseStatusCode.NORMAL_CLOSURE, "Close frame response", false);
            }
            // TODO Wait for a minute to send close frame?
            closeAndRaiseEvent(code, reason);
        }

        @Override
        public void onBinaryMessage(byte[] message) {
            if (!isConnected()) {
                return;
            }
            mCallbackHandler.onBinaryMessage(message);
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
            Log.d(TAG, "Protocol violation detected");
            // TODO send error code to remote?
            closeAndRaiseEvent(CloseStatusCode.PROTOCOL_ERROR, "Protocol violation detected");
        }

        @Override
        public void onPayloadOverflow() {
            Log.d(TAG, "Response payload size overflow");
            // TODO send error code to remote?
            closeAndRaiseEvent(CloseStatusCode.MESSAGE_TOO_BIG, "Response payload size overflow");
        }
    };
}
