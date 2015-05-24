package net.kazyx.wirespider;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

interface Handshake {
    /**
     * Try to upgrade this connection as WebSocket.
     *
     * @param uri            URI of the remote server.
     * @param requestHeaders Additional request headers. Nullable.
     */
    void tryUpgrade(URI uri, List<HttpHeader> requestHeaders);

    /**
     * Called when WebSocket handshake response is received.
     *
     * @param data List of received data.
     * @return Remaining (non-header) data.
     * @throws BufferUnsatisfiedException if received data does not contain CRLF. Waiting for the next data.
     * @throws HandshakeFailureException  if handshake failure is detected.
     */
    LinkedList<byte[]> onHandshakeResponse(LinkedList<byte[]> data) throws BufferUnsatisfiedException, HandshakeFailureException;
}
