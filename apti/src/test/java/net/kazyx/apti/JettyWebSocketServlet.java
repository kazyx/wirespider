package net.kazyx.apti;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketFrame;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.extensions.Frame;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.nio.ByteBuffer;
import java.util.List;

@WebSocket
public class JettyWebSocketServlet {
    private Session mSession;

    public static final int MAX_SIZE_1MB = 1000000;

    public static final String REJECT_KEY = "reject_upgrade";

    @OnWebSocketFrame
    public void onFrame(Frame frame) throws IOException {
        if (OpCode.CONNECTION_CLOSE == frame.getOpCode()) {
            System.out.println("JettyWebSocketServlet: close frame handled");
            WebSocketClientTest.callbackCloseFrame();
        } else if (OpCode.PING == frame.getOpCode()) {
            System.out.println("JettyWebSocketServlet: ping frame handled");
            WebSocketClientTest.callbackPingFrame();
        } else if (OpCode.PONG == frame.getOpCode()) {
            System.out.println("JettyWebSocketServlet: pong frame handled");
            WebSocketClientTest.callbackPongFrame();
        }
    }

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

    public static final String SLEEP_REQUEST = "sleep";

    public static final String PING_REQUEST = "ping";

    @OnWebSocketMessage
    public void onTextMessage(String message) throws InterruptedException, IOException {
        if (message.equals(CLOSE_REQUEST)) {
            System.out.println("JettyWebSocketServlet: close request received");
            mSession.close(1000, "Normal closure");
        } else if (message.equals(SLEEP_REQUEST)) {
            System.out.println("JettyWebSocketServlet: sleep request received. Start sleep for 2 sec");
            Thread.sleep(2000);
        } else if (message.equals(PING_REQUEST)) {
            String pingMsg = "hello";
            ByteBuffer buff = ByteBuffer.wrap(pingMsg.getBytes("UTF-8"));
            mSession.getRemote().sendPing(buff);
        } else {
            mSession.getRemote().sendStringByFuture(message);
        }
    }

    @OnWebSocketMessage
    public void onBinaryMessage(byte buf[], int offset, int length) throws UnsupportedEncodingException {
        ByteBuffer buff = ByteBuffer.wrap(buf, offset, length);
        // System.out.println("JettyWebSocketServlet: ByteBuffer: " + buff.toString());

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
