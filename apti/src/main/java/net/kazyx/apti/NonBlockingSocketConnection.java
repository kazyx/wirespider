package net.kazyx.apti;

import java.nio.ByteBuffer;
import java.util.LinkedList;

interface NonBlockingSocketConnection {
    void onConnected();

    void onClosed();

    void onDataReceived(LinkedList<ByteBuffer> data);
}
