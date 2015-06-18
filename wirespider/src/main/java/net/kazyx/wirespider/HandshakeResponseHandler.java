package net.kazyx.wirespider;

public interface HandshakeResponseHandler {

    /**
     * Called when basic WebSocket handshake is completed.<br>
     * Check Handshake response to judge whether to accept the response or not.
     *
     * @param response Response of the handshake.
     * @return Accept this {@link HandshakeResponse} or not.
     */
    boolean onReceived(HandshakeResponse response);
}
