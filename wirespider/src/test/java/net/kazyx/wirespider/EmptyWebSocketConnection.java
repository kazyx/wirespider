package net.kazyx.wirespider;

public class EmptyWebSocketConnection implements WebSocketConnection {
    @Override
    public void onTextMessage(String message) {
    }

    @Override
    public void onBinaryMessage(byte[] message) {
    }

    @Override
    public void onPong(String message) {
    }

    @Override
    public void onClosed(int code, String reason) {
    }
}
