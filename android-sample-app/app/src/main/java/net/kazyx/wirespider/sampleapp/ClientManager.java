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
import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.extension.compression.DeflateRequest;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClientManager {
    private static final String TAG = ClientManager.class.getSimpleName();

    static {
        net.kazyx.wirespider.util.Base64.setEncoder(new net.kazyx.wirespider.util.Base64.Encoder() {
            @Override
            public String encode(byte[] source) {
                return Base64.encodeToString(source, Base64.DEFAULT);
            }
        });
    }

    private final WebSocketFactory mFactory;

    public ClientManager() throws IOException {
        mFactory = new WebSocketFactory();
    }

    public void open(URI uri, final ConnectionListener listener) {
        ExtensionRequest pmdeflate = new DeflateRequest.Builder()
                .setCompressionThreshold(100)
                .build();
        List<ExtensionRequest> extensionRequests = new ArrayList<>();
        extensionRequests.add(pmdeflate);

        WebSocketHandler handler = new WebSocketHandler() {
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
        };

        final SessionRequest req = new SessionRequest.Builder(uri, handler)
                .setExtensions(extensionRequests)
                .setConnectionTimeout(10, TimeUnit.SECONDS)
                .build();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mWebSocket = mFactory.open(req);
                    listener.onConnected();
                } catch (IOException e) {
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
