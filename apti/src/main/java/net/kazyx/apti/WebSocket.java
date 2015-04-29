package net.kazyx.apti;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class WebSocket {
    private static final String TAG = WebSocket.class.getSimpleName();

    private final AsyncSource mAsync;
    private final URI mURI;
    private final WebSocketConnection mHandler;
    private final List<HttpHeader> mRequestHeaders;

    private final Object mCallbackLock = new Object();
    private boolean mIsConnected = false;

    private final SelectionHandler mSelectionHandler;

    private FrameTx mFrameTx;
    private FrameRx mFrameRx;
    private Handshake mHandshake;

    private final Object mPingPongTaskLock = new Object();
    private TimerTask mPingPongTask;

    WebSocket(AsyncSource async, URI uri, WebSocketConnection handler, List<HttpHeader> extraHeaders) {
        mURI = uri;
        mRequestHeaders = extraHeaders;
        mHandler = handler;
        mAsync = async;

        mSelectionHandler = new SelectionHandler(new NonBlockingSocketConnection() {
            @Override
            public void onConnected() {
                Logger.d(TAG, "SelectionHandler onConnected");
                onSocketConnected();
            }

            @Override
            public void onClosed() {
                Logger.d(TAG, "SelectionHandler onClosed");
                mConnectLatch.countDown();
                closeInternal();
            }

            @Override
            public void onDataReceived(final LinkedList<ByteBuffer> data) {
                Logger.d(TAG, "SelectionHandler onDataReceived");
                mAsync.safeAsyncAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!isConnected()) {
                            try {
                                LinkedList<ByteBuffer> remaining = mHandshake.onDataReceived(data);
                                mHandshake = null;
                                mIsConnected = true;
                                Logger.d(TAG, "WebSocket handshake succeed!!");
                                mConnectLatch.countDown();

                                if (data.size() != 0) {
                                    mFrameRx.onDataReceived(remaining);
                                }
                            } catch (BufferUnsatisfiedException e) {
                                // wait for the next data.
                            } catch (HandshakeFailureException e) {
                                closeInternal();
                            }
                        } else {
                            mFrameRx.onDataReceived(data);
                        }
                    }
                });
            }
        });
    }

    private void onSocketConnected() {
        mFrameTx = new Rfc6455Tx(WebSocket.this, true, mSelectionHandler);
        mFrameRx = new Rfc6455Rx(WebSocket.this);
        mHandshake = new Rfc6455Handshake();
        mHandshake.tryUpgrade(mURI, mRequestHeaders, mSelectionHandler);
    }

    CountDownLatch mConnectLatch = new CountDownLatch(1);

    /**
     * Synchronously openAsync WebSocket connection.
     *
     * @throws IOException          Failed to openAsync connection.
     * @throws InterruptedException Awaiting thread interrupted.
     */
    void connect(SocketChannel channel) throws IOException, InterruptedException {
        Logger.d(TAG, "try connect");

        final Socket socket = channel.socket();
        socket.setTcpNoDelay(true);
        // TODO bind local address here.

        channel.connect(new InetSocketAddress(mURI.getHost(), (mURI.getPort() != -1) ? mURI.getPort() : 80));
        mAsync.registerNewChannel(channel, SelectionKey.OP_CONNECT, mSelectionHandler);

        mConnectLatch.await();

        if (!isConnected()) {
            throw new IOException("Socket connection or handshake failure");
        }
    }

    /**
     * @return WebSocket connection is established or not.
     */
    public boolean isConnected() {
        return mIsConnected;
    }

    /**
     * Send text message asynchronously.
     *
     * @param message Text message to send.
     */
    public void sendTextMessageAsync(String message) {
        mFrameTx.sendTextAsync(message);
    }

    /**
     * Send binary message asynchronously.
     *
     * @param message Binary message to send.
     */
    public void sendBinaryMessageAsync(byte[] message) {
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
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                synchronized (mPingPongTaskLock) {
                    closeInternal();
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

        // Forcefully close connection 2 sec after sending close frame.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // This will never happen.
                }
                closeInternal();
            }
        }).start();
    }

    /**
     * Close TCP connection without WebSocket closing handshake.
     */
    void closeInternal() {
        mSelectionHandler.close();
        synchronized (mCallbackLock) {
            if (isConnected()) {
                mIsConnected = false;
                mHandler.onClosed();
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
     * @param code    Close status code.
     * @param message Close reason phrase.
     */
    void onCloseFrame(int code, String message) {
        if (isConnected()) {
            return;
        }
        if (code != CloseStatusCode.ABNORMAL_CLOSURE.statusCode) {
            sendCloseFrame(CloseStatusCode.NORMAL_CLOSURE, "Close frame response");
        }
        closeInternal();
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
        mHandler.onBinaryMessage(message);
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
        mHandler.onTextMessage(message);
    }

    /**
     * Called when received message violated WebSocket protocol.
     */
    void onProtocolViolation() {
        if (isConnected()) {
            return;
        }
        sendCloseFrame(CloseStatusCode.POLICY_VIOLATION, "WebSocket protocol violation");
    }
}
