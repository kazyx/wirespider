package net.kazyx.apti;

import javax.net.SocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class WebSocket {
    private final AsyncSource mAsync;
    private final URI mURI;
    private final WebSocketConnection mHandler;
    private final List<HttpHeader> mRequestHeaders;

    private boolean mIsConnected = false;

    private Socket mSocket;
    private FrameGenerator mFrameGenerator;

    private final Object mPingPongLock = new Object();
    private TimerTask mPingPongTask;

    WebSocket(AsyncSource async, URI uri, WebSocketConnection handler, List<HttpHeader> extraHeaders) {
        mURI = uri;
        mRequestHeaders = extraHeaders;
        mHandler = handler;
        mAsync = async;
    }

    /**
     * Synchronously open WebSocket connection.
     *
     * @throws IOException Failed to open connection.
     */
    void connect() throws IOException {
        String secret = HandshakeSecretUtil.createNew();

        SocketFactory factory = WebSocketClientFactory.sFactory;
        if (factory == null) {
            factory = SocketFactory.getDefault();
        }

        mSocket = factory.createSocket(mURI.getHost(), (mURI.getPort() != -1) ? mURI.getPort() : 80);

        mSocket.setTcpNoDelay(true);
        mSocket.setReuseAddress(true);

        StringBuilder sb = new StringBuilder();
        sb.append("GET ").append(mURI.getSchemeSpecificPart() == null ? "/" : mURI.getSchemeSpecificPart()).append(" HTTP/1.1\r\n");
        sb.append("Host: ").append(mURI.getHost()).append("\r\n");
        sb.append("Upgrade: websocket\r\n");
        sb.append("Connection: Upgrade\r\n");
        sb.append("Sec-WebSocket-Key: ").append(secret).append("\r\n");
        sb.append("Sec-WebSocket-Version: 13\r\n");

        if (mRequestHeaders != null && mRequestHeaders.size() != 0) {
            for (HttpHeader header : mRequestHeaders) {
                sb.append(header.toHeaderLine()).append("\r\n");
            }
        }

        sb.append("\r\n");

        OutputStream os = new BufferedOutputStream(mSocket.getOutputStream());
        os.write(sb.toString().getBytes("UTF-8"));

        InputStream is = new BufferedInputStream(mSocket.getInputStream());

        HttpHeaderReader headerReader = new HttpHeaderReader(is);

        HttpStatusLine statusLine = headerReader.getStatusLine();
        if (statusLine.statusCode != 101) {
            throw new IOException("WebSocket opening handshake failed: " + statusLine.statusCode);
        }

        List<HttpHeader> resHeaders = headerReader.getHeaderFields();

        boolean validated = false;
        for (HttpHeader header : resHeaders) {
            if (header.key.equalsIgnoreCase(HttpHeader.SEC_WEBSOCKET_ACCEPT)) {
                String expected = HandshakeSecretUtil.convertForValidation(secret);
                validated = header.values.get(0).equals(expected);
                if (!validated) {
                    throw new IOException("WebSocket opening handshake failed: Invalid Sec-WebSocket-Accept header value");
                }
                break;
            }
        }

        if (!validated) {
            throw new IOException("WebSocket opening handshake failed: No Sec-WebSocket-Accept header");
        }

        mIsConnected = true;
        mFrameGenerator = new Rfc6455FrameGenerator(true);
        mAsync.mConnectionThreadPool.submit(new Rfc6455Reader(is, this));
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
        sendFrameAsync(mFrameGenerator.createTextFrame(message));
    }

    /**
     * Send binary message asynchronously.
     *
     * @param message Binary message to send.
     */
    public void sendBinaryMessageAsync(byte[] message) {
        sendFrameAsync(mFrameGenerator.createBinaryFrame(message));
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
                synchronized (mPingPongLock) {
                    closeInternal();
                }
            }
        };

        synchronized (mPingPongLock) {
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
        sendFrameAsync(mFrameGenerator.createPingFrame());
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
        if (!mIsConnected) {
            return;
        }

        mAsync.mActionThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                sendCloseFrame(code, reason);
            }
        });
    }

    private boolean mIsCloseSent = false;

    private void sendCloseFrame(CloseStatusCode code, String reason) {
        synchronized (mSendLock) {
            sendFrame(mFrameGenerator.createCloseFrame(code, reason));
            mIsCloseSent = true;
        }

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
    private void closeInternal() {
        synchronized (mSendLock) {
            if (mIsConnected) {
                mIsConnected = false;
                IOUtil.close(mSocket);
                mHandler.onClosed();
            }
        }
    }

    private void sendFrameAsync(final byte[] frame) {
        mAsync.mActionThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                sendFrame(frame);
            }
        });
    }

    private final Object mSendLock = new Object();

    private void sendFrame(byte[] frame) {
        synchronized (mSendLock) {
            try {
                if (!mIsConnected || mIsCloseSent) {
                    return;
                }
                OutputStream outputStream = new BufferedOutputStream(mSocket.getOutputStream());
                outputStream.write(frame);
                outputStream.flush();
            } catch (IOException e) {
                closeInternal();
            }
        }
    }

    /**
     * Called when received ping control frame.
     *
     * @param message Ping message.
     */
    void onPingFrame(String message) {
        sendFrameAsync(mFrameGenerator.createPongFrame(message));
    }

    /**
     * Called when received pong control frame.
     *
     * @param message Pong message.
     */
    void onPongFrame(String message) {
        // TODO should check pong message is same as ping message we've sent.
        synchronized (mPingPongLock) {
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
        if (code != CloseStatusCode.ABNORMAL_CLOSURE.statusCode || !mIsCloseSent) {
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
        mHandler.onBinaryMessage(message);
    }

    /**
     * Called when received text message.
     *
     * @param message Received text message.
     */
    void onTextMessage(String message) {
        mHandler.onTextMessage(message);
    }

    /**
     * Called when received message violated WebSocket protocol.
     */
    void onProtocolViolation() {
        sendFrame(mFrameGenerator.createCloseFrame(CloseStatusCode.POLICY_VIOLATION, "WebSocket protocol violation"));
    }
}
