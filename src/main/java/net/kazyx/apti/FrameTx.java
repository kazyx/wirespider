package net.kazyx.apti;

interface FrameTx {
    void sendTextFrame(String data);

    void sendBinaryFrame(byte[] data);

    void sendPingFrame();

    void sendPongFrame(String message);

    void sendCloseFrame(CloseStatusCode code, String reason);
}
