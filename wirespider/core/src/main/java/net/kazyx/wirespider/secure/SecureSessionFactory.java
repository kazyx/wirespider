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

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.NoSuchAlgorithmException;

public class SecureSessionFactory implements SessionFactory {
    private final SSLContext mSslContext;

    public SecureSessionFactory(SSLContext context) {
        if (context == null) {
            try {
                mSslContext = SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        } else {
            mSslContext = context;
        }
    }

    @Override
    public SecureSession createNew(SelectionKey key) throws IOException {
        return new SecureSession(mSslContext, key);
    }
}
