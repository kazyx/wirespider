/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.secure;

import net.kazyx.wirespider.SessionFactory;
import net.kazyx.wirespider.util.WsLog;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.NoSuchAlgorithmException;

public class SecureSessionFactory implements SessionFactory {
    private static final String TAG = SecureSessionFactory.class.getSimpleName();

    private static SSLContext sSslContext;

    @Override
    public SecureSession createNew(SelectionKey key) throws IOException {
        try {
            return new SecureSession(getSslContext(), key);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public static void setSslContext(SSLContext context) {
        WsLog.d(TAG, "Non default SSLContext is set");
        sSslContext = context;
    }

    private static SSLContext getSslContext() throws NoSuchAlgorithmException {
        if (sSslContext == null) {
            return SSLContext.getDefault();
        } else {
            return sSslContext;
        }
    }
}
