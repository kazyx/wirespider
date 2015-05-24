package net.kazyx.wirespider;

import static org.junit.Assert.fail;

public class FailOnCallbackRxListener implements FrameRx.Listener {
    public FailOnCallbackRxListener() {
    }

    @Override
    public void onPingFrame(String message) {
        fail();
    }

    @Override
    public void onPongFrame(String message) {
        fail();
    }

    @Override
    public void onCloseFrame(int code, String reason) {
        fail();
    }

    @Override
    public void onBinaryMessage(byte[] message) {
        fail();
    }

    @Override
    public void onTextMessage(String message) {
        fail();
    }

    @Override
    public void onProtocolViolation() {
        fail();
    }

    @Override
    public void onPayloadOverflow() {
        fail();
    }
}
