package net.kazyx.apti;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

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

    @OnWebSocketMessage
    public void onBinaryMessage(byte buf[], int offset, int length) throws UnsupportedEncodingException {
        System.out.println("JettyWebSocketServlet: onBinaryMessage: " + length);
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
