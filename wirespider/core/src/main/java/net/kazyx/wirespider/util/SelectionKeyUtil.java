/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.util;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;

public class SelectionKeyUtil {
    private SelectionKeyUtil() {
    }

    public static void interestOps(SelectionKey key, int ops) throws IOException {
        try {
            key.interestOps(ops);
        } catch (CancelledKeyException e) {
            throw new IOException(e);
        }
    }
}
