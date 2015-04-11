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
    private WebSocketParser mParser = null;

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
        mParser = new Rfc6455Parser(true);
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
     * @throws IOException Connection is already closed.
     */
    public void sendTextMessageAsync(String message) throws IOException {
        sendFrameAsync(mParser.createTextFrame(message));
    }

    /**
     * Send binary message asynchronously.
     *
     * @param message Binary message to send.
     * @throws IOException Connection is already closed.
     */
    public void sendBinaryMessageAsync(byte[] message) throws IOException {
        sendFrameAsync(mParser.createBinaryFrame(message));
    }

    /**
     * Try send PING and wait for PONG.<br>
     * If PONG frame does not come within timeout, WebSocket connection will be closed.
     *
     * @param timeout Timeout value.
     * @param unit    TImeUnit of timeout value.
     * @throws IOException Connection is already closed.
     */
    public void checkConnectionAsync(long timeout, TimeUnit unit) throws IOException {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                closeInternal();
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
        sendFrameAsync(mParser.createPingFrame());
    }

    /**
     * Close WebSocket connection gracefully.<br>
     * If it is already closed, nothing happens.
     */
    public void closeAsync() {
        closeAsync(CloseStatusCode.NORMAL_CLOSURE, "normal closure");
    }

    /**
     * Close WebSocket connection gracefully.<br>
     * If it is already closed, nothing happens.
     *
     * @param code
     * @param reason
     */
    public void closeAsync(CloseStatusCode code, String reason) {
        try {
            sendFrameAsync(mParser.createCloseFrame(code, reason));
        } catch (IOException e) {
            // Ignore close IOException
        }
    }

    /**
     * Close TCP connection without WebSocket closing handshake.
     */
    private void closeInternal() {
        if (mIsConnected) {
            mHandler.onClosed();
            mIsConnected = false;
        }

        IOUtil.close(mSocket);
    }

    private void sendFrameAsync(final byte[] frame) throws IOException {
        if (!mIsConnected) {
            throw new IOException("WebSocket not opened");
        }

        mAsync.mActionThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                sendFrame(frame);
            }
        });
    }

    private void sendFrame(byte[] frame) {
        try {
            if (!mIsConnected) {
                return;
            }
            OutputStream outputStream = new BufferedOutputStream(mSocket.getOutputStream());
            outputStream.write(frame);
            outputStream.flush();
        } catch (IOException e) {
            closeInternal();
        }
    }

    void onPingFrame(String message) {
        try {
            sendFrameAsync(mParser.createPongFrame());
        } catch (IOException e) {
            // This will never happen.
        }
    }

    void onPongFrame(String message) {
        synchronized (mPingPongLock) {
            if (mPingPongTask != null) {
                mPingPongTask.cancel();
                mPingPongTask = null;
            }
        }
    }

    void onCloseFrame(int code, String message) {
        // TODO closeAsync handshake
        closeInternal();
        mHandler.onClosed();
    }

    void onBinaryMessage(byte[] message) {
        mHandler.onBinaryMessage(message);
    }

    void onTextMessage(String message) {
        mHandler.onTextMessage(message);
    }
}
