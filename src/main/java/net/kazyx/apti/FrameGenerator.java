package net.kazyx.apti;

public interface FrameGenerator {
    byte[] createTextFrame(String data);

    byte[] createBinaryFrame(byte[] data);

    byte[] createPingFrame();

    byte[] createPongFrame(String message);

    byte[] createCloseFrame(CloseStatusCode code, String reason);
}
