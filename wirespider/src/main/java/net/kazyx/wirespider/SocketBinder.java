package net.kazyx.wirespider;

import java.io.IOException;
import java.net.Socket;

public interface SocketBinder {
    /**
     * Bind socket to the specified local address or network interface.
     *
     * @param socket Unbound socket.
     * @throws IOException if failed to bind the socket.
     */
    void bind(Socket socket) throws IOException;
}
