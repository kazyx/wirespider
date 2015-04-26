package net.kazyx.apti;

import java.nio.ByteBuffer;
import java.util.LinkedList;

interface FrameRx {
    void onDataReceived(LinkedList<ByteBuffer> data);
}
