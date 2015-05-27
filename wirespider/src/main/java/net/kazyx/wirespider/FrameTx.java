package net.kazyx.wirespider;

interface FrameTx {
    void sendTextAsync(String data);

    void sendBinaryAsync(byte[] data);

    void sendPingAsync(String message);

    void sendPongAsync(String message);

    void sendCloseAsync(CloseStatusCode code, String reason);
}
