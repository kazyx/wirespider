/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.sampleapp;

import android.util.Base64;
import android.util.Log;

import net.kazyx.wirespider.SessionRequest;
import net.kazyx.wirespider.WebSocket;
import net.kazyx.wirespider.WebSocketFactory;
import net.kazyx.wirespider.WebSocketHandler;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientManager {
    private static final String TAG = ClientManager.class.getSimpleName();

    static {
        net.kazyx.wirespider.Base64.encoder(new net.kazyx.wirespider.Base64.Encoder() {
            @Override
            public String encode(byte[] source) {
                return Base64.encodeToString(source, android.util.Base64.DEFAULT);
            }
        });
    }

    private final WebSocketFactory mFactory;

    public ClientManager() throws IOException {
        mFactory = new WebSocketFactory();
    }

    public void open(URI uri, final ConnectionListener listener) {
        final SessionRequest req = new SessionRequest.Builder(uri, new WebSocketHandler() {
            @Override
            public void onTextMessage(String message) {
                if (mHandlerRef != null) {
                    WebSocketHandler handler = mHandlerRef.get();
                    if (handler != null) {
                        handler.onTextMessage(message);
                    }
                }
            }

            @Override
            public void onBinaryMessage(byte[] message) {
                if (mHandlerRef != null) {
                    WebSocketHandler handler = mHandlerRef.get();
                    if (handler != null) {
                        handler.onBinaryMessage(message);
                    }
                }
            }

            @Override
            public void onClosed(int code, String reason) {
                if (mHandlerRef != null) {
                    WebSocketHandler handler = mHandlerRef.get();
                    if (handler != null) {
                        handler.onClosed(code, reason);
                    }
                }
            }
        }).build();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mWebSocket = mFactory.openAsync(req).get(5, TimeUnit.SECONDS);
                    listener.onConnected();
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    Log.w(TAG, e);
                    listener.onConnectionFailed(e);
                }
            }
        }).start();
    }

    private WeakReference<WebSocketHandler> mHandlerRef;

    public void setWebSocketHandler(WebSocketHandler handler) {
        mHandlerRef = new WeakReference<>(handler);
    }

    private WebSocket mWebSocket;

    public WebSocket getWebSocket() {
        return mWebSocket;
    }

    public interface ConnectionListener {
        void onConnected();

        void onConnectionFailed(Exception e);
    }

    public void dispose() {
        mFactory.destroy();
    }
}
