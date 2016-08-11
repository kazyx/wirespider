/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.test;

import net.kazyx.wirespider.FrameRx;

import java.io.IOException;
import java.nio.ByteBuffer;

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
    public void onInvalidPayloadError(IOException e) {
        fail();
    }

    @Override
    public void onBinaryMessage(ByteBuffer message) {
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
