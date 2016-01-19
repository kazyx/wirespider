/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.NoSuchAlgorithmException;

class SecureSessionFactory implements SessionFactory {
    private final SSLContext mSslContext;

    SecureSessionFactory(SSLContext context) throws NoSuchAlgorithmException {
        if (context == null) {
            mSslContext = SSLContext.getDefault();
        } else {
            mSslContext = context;
        }
    }

    @Override
    public SecureSession createNew(SelectionKey key) throws IOException {
        return new SecureSession(mSslContext, key);
    }
}
