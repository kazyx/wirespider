package net.kazyx.wirespider;

public class SilentEventHandler extends WebSocketHandler {
    @Override
    public void onTextMessage(String message) {
    }

    @Override
    public void onBinaryMessage(byte[] message) {
    }

    @Override
    public void onClosed(int code, String reason) {
    }
}
