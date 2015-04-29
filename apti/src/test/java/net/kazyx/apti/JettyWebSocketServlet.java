package net.kazyx.apti;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;

@org.eclipse.jetty.websocket.api.annotations.WebSocket
public class JettyWebSocketServlet {
    private Session mSession;

    @OnWebSocketConnect
    public void onConnected(Session session) {
        System.out.println("JettyWebSocketServlet: onConnected");
        mSession = session;
    }

    @OnWebSocketMessage
    public void onTextMessage(String message) {
        System.out.println("JettyWebSocketServlet: onTextMessage: " + message);
    }

    @OnWebSocketClose
    public void onClosed(int statusCode, String reason) {
        System.out.println("JettyWebSocketServlet: onClosed: " + statusCode);
    }
}
