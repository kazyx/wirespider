/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.sampleapp.echoserver;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

@WebSocket
public class JettyWebSocketServlet {
    private Session mSession;

    @OnWebSocketConnect
    public void onConnected(Session session) {
        mSession = session;
    }

    @OnWebSocketMessage
    public void onTextMessage(String message) throws InterruptedException, IOException {
        mSession.getRemote().sendStringByFuture(message);
    }

    @OnWebSocketMessage
    public void onBinaryMessage(byte buf[], int offset, int length) throws UnsupportedEncodingException {
        ByteBuffer buff = ByteBuffer.wrap(buf, offset, length);
        mSession.getRemote().sendStringByFuture("Received binary message with length " + length);
    }
}
