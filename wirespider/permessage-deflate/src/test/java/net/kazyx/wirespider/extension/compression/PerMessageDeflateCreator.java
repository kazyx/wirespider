/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension.compression;

public class PerMessageDeflateCreator {
    public static PerMessageDeflate create(int threshold) {
        return new PerMessageDeflate(threshold);
    }
}
