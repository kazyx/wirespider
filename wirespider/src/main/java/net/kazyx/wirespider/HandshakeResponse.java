package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.Extension;

import java.util.Collections;
import java.util.List;

public class HandshakeResponse {
    private final List<Extension> mActiveExtensions;

    private String mActiveProtocol;

    HandshakeResponse(List<Extension> extensions, String protocol) {
        if (extensions == null) {
            mActiveExtensions = Collections.emptyList();
        } else {
            mActiveExtensions = Collections.unmodifiableList(extensions);
        }
        mActiveProtocol = protocol;
    }

    /**
     * Provide List of accepted WebSocket extensions.
     *
     * @return Copy of accepted WebSocket extensions.
     */
    public List<Extension> extensions() {
        return mActiveExtensions;
    }

    /**
     * @return Active protocol of this session, or {@code null} if no protocol is defined.
     */
    public String protocol() {
        return mActiveProtocol;
    }
}
