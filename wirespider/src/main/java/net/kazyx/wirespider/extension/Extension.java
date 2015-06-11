package net.kazyx.wirespider.extension;

/**
 * WebSocket extension.
 */
public interface Extension {
    /**
     * @return Name of extension method.
     */
    String name();

    /**
     * @param parameters Handshake response for this extension.
     * @return Accept the response or not.
     */
    boolean accept(String[] parameters);
}
