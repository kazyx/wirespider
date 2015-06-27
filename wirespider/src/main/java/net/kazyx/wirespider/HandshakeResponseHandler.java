package net.kazyx.wirespider;

public interface HandshakeResponseHandler {

    /**
     * Called when fundamental WebSocket handshake is completed.<br>
     * Delegates evaluation of extension and protocol in response header.
     *
     * @param response Response of the handshake.
     * @return Accept this {@link HandshakeResponse} or not.
     */
    boolean onReceived(HandshakeResponse response);
}
