/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.sampleapp;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;

import net.kazyx.wirespider.SecureTransport;
import net.kazyx.wirespider.SessionRequest;
import net.kazyx.wirespider.WebSocket;
import net.kazyx.wirespider.WebSocketFactory;
import net.kazyx.wirespider.WebSocketHandler;
import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.extension.compression.CompressionStrategy;
import net.kazyx.wirespider.extension.compression.DeflateRequest;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientManager {
    private static final String TAG = ClientManager.class.getSimpleName();

    static {
        net.kazyx.wirespider.Base64.setEncoder(new net.kazyx.wirespider.Base64.Encoder() {
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

    public void enableTls() throws GooglePlayServicesNotAvailableException, GooglePlayServicesRepairableException, NoSuchAlgorithmException {
        ProviderInstaller.installIfNeeded(SampleApp.getAppContext()); // For Android 4.4 and lower
        SecureTransport.enable(mFactory); // Enable WebSocket over TLS
    }

    public void open(URI uri, final ConnectionListener listener) {
        List<ExtensionRequest> extensionRequests = new ArrayList<>();
        extensionRequests.add(new DeflateRequest.Builder().setStrategy(new CompressionStrategy() {
            @Override
            public int minSizeInBytes() {
                return 100; // If sending message size is over 100 byte, compression will be performed.
            }
        }).build());

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
        }).setExtensions(extensionRequests).build();

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
