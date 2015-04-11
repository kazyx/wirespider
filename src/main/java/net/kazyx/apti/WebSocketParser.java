package net.kazyx.apti;

public interface WebSocketParser {
    byte[] createTextFrame(String data);

    byte[] createBinaryFrame(byte[] data);

    byte[] createPingFrame();

    byte[] createPongFrame();

    byte[] createCloseFrame(CloseStatusCode code, String reason);
}
