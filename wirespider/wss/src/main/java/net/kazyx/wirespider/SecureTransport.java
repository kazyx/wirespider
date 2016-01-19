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
import java.security.NoSuchAlgorithmException;

public class SecureTransport {
    private static final String WSS_SCHEME = "wss";

    /**
     * Enable secure connection for "wss" URI scheme on the given {@link WebSocketFactory} with default {@link SSLContext}.
     *
     * @param factory {@link WebSocketFactory} to enable secure connection.
     * @throws NoSuchAlgorithmException Failed to start secure session engine.
     */
    public static void enable(WebSocketFactory factory) throws NoSuchAlgorithmException {
        enable(factory, null);
    }

    /**
     * Enable secure connection for "wss" URI scheme on the given {@link WebSocketFactory}.
     *
     * @param factory {@link WebSocketFactory} to enable secure connection.
     * @param context Assign {@link SSLContext} for the given {@link WebSocketFactory} or {@code null} for the default.
     * @throws NoSuchAlgorithmException Failed to start secure session engine.
     */
    public static void enable(WebSocketFactory factory, SSLContext context) throws NoSuchAlgorithmException {
        factory.socketEngine().registerFactory(new SecureSessionFactory(context), WSS_SCHEME);
    }
}
