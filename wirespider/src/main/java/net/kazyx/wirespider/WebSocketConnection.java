package net.kazyx.wirespider;

/**
 * WebSocket event handler.
 */
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
     * Received Pong frame.
     *
     * @param message Pong message. This should be equal to what we sent with Ping frame.
     */
    void onPong(String message);

    /**
     * WebSocket closed.
     *
     * @param code   Close status code
     * @param reason Reason phrase.
     */
    void onClosed(int code, String reason);
}
