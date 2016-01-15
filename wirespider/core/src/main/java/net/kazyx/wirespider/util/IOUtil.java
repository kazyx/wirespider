/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Selector;

public final class IOUtil {
    private IOUtil() {
    }

    /**
     * Close a {@link Closeable} silently.
     *
     * @param closeable Closeable
     */
    public static void close(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // Nothing to do
        }
    }

    /**
     * Close a {@link Selector} silently.<br>
     * Workaround for Android 4.3 or older on which {@link Selector} does not implement Closeable.
     *
     * @param selector Selector.
     */
    public static void close(Selector selector) {
        if (selector == null) {
            return;
        }
        try {
            selector.close();
        } catch (IOException e) {
            // Nothing to do
        }
    }
}
