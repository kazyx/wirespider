package net.kazyx.apti;

import java.nio.ByteBuffer;
import java.util.LinkedList;

interface FrameRx {
    /**
     * Called when WebSocket frame data is received.
     *
     * @param data List of received data.
     */
    void onDataReceived(LinkedList<ByteBuffer> data);
}
