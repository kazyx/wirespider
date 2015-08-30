/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.util;

public class ArgumentCheck {
    private ArgumentCheck() {
    }

    public static void rejectNullArgs(Object... args) {
        for (Object arg : args) {
            rejectNull(arg);
        }
    }

    public static void rejectNull(Object arg) {
        if (arg == null) {
            throw new NullPointerException("Null argument rejected");
        }
    }
}
