package net.kazyx.apti;

public interface WebSocketConnection {
    /**
     * Received text message.
     *
     * @param message Received text message
     */
    void onTextMessage(String message);

    /**
     * Received binary message.
     *
     * @param message Received binary message
     */
    void onBinaryMessage(byte[] message);

    /**
     * WebSocket closed.
     */
    void onClosed();
}
