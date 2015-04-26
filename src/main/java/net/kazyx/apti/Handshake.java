package net.kazyx.apti;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

interface Handshake {
    void tryUpgrade(URI uri, List<HttpHeader> requestHeaders, SelectionHandler handler);

    LinkedList<ByteBuffer> onDataReceived(LinkedList<ByteBuffer> data) throws BufferUnsatisfiedException, HandshakeFailureException;
}
