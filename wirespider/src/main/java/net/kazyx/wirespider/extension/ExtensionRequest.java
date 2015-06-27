package net.kazyx.wirespider.extension;

import net.kazyx.wirespider.http.HttpHeader;

public interface ExtensionRequest {
    /**
     * @return HTTP header for handshake request.
     */
    HttpHeader requestHeader();

    /**
     * @return Extension instance result for this request.
     */
    Extension extension();
}
