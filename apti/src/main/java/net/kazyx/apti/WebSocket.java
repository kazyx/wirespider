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

    final AsyncSource getAsync() {
        return mAsync;
    }

    private final URI mURI;

    final URI getRemoteURI() {
        return mURI;
    }

    private final SocketChannel mSocketChannel;

    final SocketChannel getSocketChannel() {
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

    private final SelectionHandler mSelectionHandler;

    final SelectionHandler getSelectionHandler() {
        return mSelectionHandler;
    }

    private final FrameTx mFrameTx;
    private final FrameRx mFrameRx;
    private final Handshake mHandshake;

    final Handshake getHandshake() {
        return mHandshake;
    }

    private final Object mPingPongTaskLock = new Object();
    private TimerTask mPingPongTask;

    WebSocket(AsyncSource async, URI uri, SocketChannel ch, WebSocketConnection handler, boolean isClient) {
        mURI = uri;
        mCallbackHandler = handler;
        mAsync = async;
        mSocketChannel = ch;

        mSelectionHandler = new SelectionHandler(new NonBlockingSocketConnection() {
            @Override
            public void onConnected() {
                onSocketConnected();
            }

            @Override
            public void onClosed() {
                // Logger.d(TAG, "SelectionHandler onClosed");
                if (mIsHandshakeCompleted) {
                    closeNow();
                } else {
                    onHandshakeFailed();
                }
            }

            @Override
            public void onDataReceived(final LinkedList<ByteBuffer> data) {
                // Logger.d(TAG, "SelectionHandler onDataReceived");
                if (!isConnected()) {
                    try {
                        LinkedList<ByteBuffer> remaining = mHandshake.onDataReceived(data);
                        mIsHandshakeCompleted = true;
                        mIsConnected = true;
                        onHandshakeCompleted();

                        if (remaining.size() != 0) {
                            mFrameRx.onDataReceived(remaining);
                        }
                    } catch (BufferUnsatisfiedException e) {
                        // wait for the next data.
                    } catch (HandshakeFailureException e) {
                        closeNow();
                    }
                } else {
                    mFrameRx.onDataReceived(data);
                }
            }
        });

        mFrameTx = new Rfc6455Tx(WebSocket.this, mSelectionHandler, isClient);
        mFrameRx = new Rfc6455Rx(WebSocket.this);
        mHandshake = new Rfc6455Handshake(mSelectionHandler, isClient);
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
        if (!isConnected()) {
            return;
        }

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                synchronized (mPingPongTaskLock) {
                    closeNow();
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
     * Same as {@link #closeAsync(CloseStatusCode, String)} with {@link CloseStatusCode#NORMAL_CLOSURE}.
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
                mSelectionHandler.close();
                invokeOnClosed(CloseStatusCode.NORMAL_CLOSURE.statusCode, "Normal Closure");
            }
        });
    }

    /**
     * Close TCP connection without WebSocket closing handshake.
     */
    public void closeNow() {
        if (!isConnected()) {
            return;
        }

        mSelectionHandler.close();
        IOUtil.close(mSocketChannel);
        invokeOnClosed(CloseStatusCode.NORMAL_CLOSURE.statusCode, "Normal Closure");
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
        mSelectionHandler.close();
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
        Logger.d(TAG, "onProtocolViolation");
        if (!isConnected()) {
            return;
        }
        sendCloseFrame(CloseStatusCode.POLICY_VIOLATION, "WebSocket protocol violation");
        mSelectionHandler.close();
        invokeOnClosed(CloseStatusCode.POLICY_VIOLATION.statusCode, "WebSocket protocol violation");
    }
}
