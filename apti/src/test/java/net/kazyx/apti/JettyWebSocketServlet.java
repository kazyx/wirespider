package net.kazyx.apti;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.nio.ByteBuffer;
import java.util.List;

@org.eclipse.jetty.websocket.api.annotations.WebSocket
public class JettyWebSocketServlet {
    private Session mSession;

    public static final int MAX_SIZE_1MB = 1000000;

    @OnWebSocketConnect
    public void onConnected(Session session) {
        session.getPolicy().setMaxBinaryMessageBufferSize(MAX_SIZE_1MB);
        session.getPolicy().setMaxBinaryMessageSize(MAX_SIZE_1MB);
        session.getPolicy().setMaxTextMessageBufferSize(MAX_SIZE_1MB);
        session.getPolicy().setMaxTextMessageSize(MAX_SIZE_1MB);

        List<HttpCookie> cookies = session.getUpgradeRequest().getCookies();
        if (!cookies.isEmpty()) {
            WebSocketClientTest.callbackCookies(cookies);
        }
        WebSocketClientTest.callbackHeaders(session.getUpgradeRequest().getHeaders());

        mSession = session;
    }

    public static final String CLOSE_REQUEST = "close";

    @OnWebSocketMessage
    public void onTextMessage(String message) {
        if (message.equals(CLOSE_REQUEST)) {
            System.out.println("JettyWebSocketServlet: close request received");
            mSession.close(1000, "Normal closure");
        } else {
            mSession.getRemote().sendStringByFuture(message);
        }
    }

    @OnWebSocketMessage
    public void onBinaryMessage(byte buf[], int offset, int length) throws UnsupportedEncodingException {
        ByteBuffer buff = ByteBuffer.wrap(buf, offset, length);
        System.out.println("JettyWebSocketServlet: ByteBuffer: " + buff.toString());

        try {
            mSession.getRemote().sendBytes(buff);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnWebSocketClose
    public void onClosed(int statusCode, String reason) {
    }
}
