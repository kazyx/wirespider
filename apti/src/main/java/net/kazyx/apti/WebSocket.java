package net.kazyx.apti;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket end point.
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

    private final WebSocketConnection mCallbackHandler;

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

    private final Object mPingPongTaskLock = new Object();
    private TimerTask mPingPongTask;

    WebSocket(AsyncSource async, URI uri, SocketChannel ch, WebSocketConnection handler, int maxPayload, boolean isClient) {
        mURI = uri;
        mCallbackHandler = handler;
        mAsync = async;
        mSocketChannel = ch;

        mSocketChannelProxy = new SocketChannelProxy(new NonBlockingSocketConnection() {
            @Override
            public void onConnected() {
                onSocketConnected();
            }

            @Override
            public void onClosed() {
                // AptiLog.d(TAG, "SocketChannelProxy onClosed");
                if (mIsHandshakeCompleted) {
                    closeAndRaiseEvent(CloseStatusCode.ABNORMAL_CLOSURE, "Socket error detected");
                } else {
                    onHandshakeFailed();
                }
            }

            @Override
            public void onDataReceived(final LinkedList<ByteBuffer> data) {
                // AptiLog.d(TAG, "SocketChannelProxy onDataReceived");
                if (!isConnected()) {
                    try {
                        LinkedList<ByteBuffer> remaining = mHandshake.onHandshakeResponse(data);
                        mIsHandshakeCompleted = true;
                        mIsConnected = true;
                        onHandshakeCompleted();

                        if (remaining.size() != 0) {
                            mFrameRx.onDataReceived(remaining);
                        }
                    } catch (BufferUnsatisfiedException e) {
                        // wait for the next data.
                    } catch (HandshakeFailureException e) {
                        AptiLog.d(TAG, "HandshakeFailureException: " + e.getMessage());
                        closeAndRaiseEvent(CloseStatusCode.PROTOCOL_ERROR, "Handshake failure");
                    }
                } else {
                    mFrameRx.onDataReceived(data);
                }
            }
        });

        mFrameTx = new Rfc6455Tx(WebSocket.this, mSocketChannelProxy, isClient);
        mFrameRx = new Rfc6455Rx(WebSocket.this, maxPayload);
        mHandshake = new Rfc6455Handshake(mSocketChannelProxy, isClient);
    }

    abstract void onSocketConnected();

    abstract void onHandshakeFailed();

    abstract void onHandshakeCompleted();

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
     * Send binary message asynchronously.
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
     * Try send PING and wait for PONG.<br>
     * If PONG frame does not come within timeout, WebSocket connection will be closed.
     *
     * @param timeout Timeout value.
     * @param unit    TImeUnit of timeout value.
     */
    public void checkConnectionAsync(long timeout, TimeUnit unit) {
        ArgumentCheck.rejectNull(unit);
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout value minus");
        }
        if (!isConnected()) {
            return;
        }

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                synchronized (mPingPongTaskLock) {
                    closeAndRaiseEvent(CloseStatusCode.GOING_AWAY, "No response for Ping frame");
                }
            }
        };

        synchronized (mPingPongTaskLock) {
            if (mPingPongTask != null) {
                mPingPongTask.cancel();
            }
            mPingPongTask = task;

            try {
                mAsync.mTimer.schedule(task, unit.toMillis(timeout));
            } catch (IllegalStateException e) {
                throw new RejectedExecutionException(e);
            }
        }

        mFrameTx.sendPingAsync();
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
     * @param code   Close status code to send.
     * @param reason Close reason phrase to send.
     */
    public void closeAsync(final CloseStatusCode code, final String reason) {
        ArgumentCheck.rejectNullArgs(code, reason);
        if (!isConnected()) {
            return;
        }

        sendCloseFrame(code, reason);
    }

    private void sendCloseFrame(CloseStatusCode code, String reason) {
        mFrameTx.sendCloseAsync(code, reason);

        mAsync.safeAsync(new Runnable() {
            @Override
            public void run() {
                // TODO Wait for a minute to send close frame?
                closeNow();
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
        if (!isConnected()) {
            return;
        }

        mSocketChannelProxy.close();
        IOUtil.close(mSocketChannel);
        invokeOnClosed(status.asNumber(), message);
    }

    private void invokeOnClosed(int code, String reason) {
        synchronized (mCloseCallbackLock) {
            if (isConnected()) {
                mIsConnected = false;
                mCallbackHandler.onClosed(code, reason);
            }
        }
    }

    /**
     * Called when received ping control frame.
     *
     * @param message Ping message.
     */
    void onPingFrame(String message) {
        if (!isConnected()) {
            return;
        }
        mFrameTx.sendPongAsync(message);
    }

    /**
     * Called when received pong control frame.
     *
     * @param message Pong message.
     */
    void onPongFrame(String message) {
        if (!isConnected()) {
            return;
        }
        // TODO should check pong message is same as ping message we've sent.
        synchronized (mPingPongTaskLock) {
            if (mPingPongTask != null) {
                mPingPongTask.cancel();
                mPingPongTask = null;
            }
        }
    }

    /**
     * Called when received close control frame or Connection is closed abnormally..
     *
     * @param code   Close status code.
     * @param reason Close reason phrase.
     */
    void onCloseFrame(int code, String reason) {
        if (!isConnected()) {
            return;
        }
        if (code != CloseStatusCode.ABNORMAL_CLOSURE.statusCode) {
            sendCloseFrame(CloseStatusCode.NORMAL_CLOSURE, "Close frame response");
        }
        mSocketChannelProxy.close();
        invokeOnClosed(code, reason);
    }

    /**
     * Called when received binary message.
     *
     * @param message Received binary message.
     */
    void onBinaryMessage(byte[] message) {
        if (!isConnected()) {
            return;
        }
        mCallbackHandler.onBinaryMessage(message);
    }

    /**
     * Called when received text message.
     *
     * @param message Received text message.
     */
    void onTextMessage(String message) {
        if (!isConnected()) {
            return;
        }
        mCallbackHandler.onTextMessage(message);
    }

    /**
     * Called when received message violated WebSocket protocol.
     */
    void onProtocolViolation() {
        AptiLog.d(TAG, "onProtocolViolation");
        // TODO send error code to remote?
        closeAndRaiseEvent(CloseStatusCode.PROTOCOL_ERROR, "Protocol violation detected");
    }

    /**
     * Called when received payload size is larger than maximum response payload size setting.
     */
    void onPayloadOverflow() {
        AptiLog.d(TAG, "Response payload size overflow");
        // TODO send error code to remote?
        closeAndRaiseEvent(CloseStatusCode.MESSAGE_TOO_BIG, "Response payload size overflow");
    }
}
