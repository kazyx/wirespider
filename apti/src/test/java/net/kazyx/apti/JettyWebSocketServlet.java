package net.kazyx.apti;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;

@org.eclipse.jetty.websocket.api.annotations.WebSocket
public class JettyWebSocketServlet {
    private Session mSession;

    private static int numConnected = 0;

    @OnWebSocketConnect
    public void onConnected(Session session) {
        mSession = session;
    }

    @OnWebSocketMessage
    public void onTextMessage(String message) {
        //System.out.println("JettyWebSocketServlet: onTextMessage: " + message);
        if (message.startsWith("echo")) {
            mSession.getRemote().sendStringByFuture(message);
        } else if (message.equals("close")) {
            System.out.println("JettyWebSocketServlet: close request received");
            mSession.close(1000, "Normal closure");
        }
    }

    @OnWebSocketClose
    public void onClosed(int statusCode, String reason) {
    }
}
