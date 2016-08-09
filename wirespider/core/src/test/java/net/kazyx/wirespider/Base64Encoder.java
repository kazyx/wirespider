/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.Base64;

import java.lang.reflect.Method;

public class Base64Encoder implements Base64.Encoder {
    @Override
    public String encode(byte[] source) {
        try {
            Class<?> c = Class.forName("android.util.Base64");
            Method m = c.getDeclaredMethod("encodeToString", byte[].class, int.class);
            return (String) m.invoke(null, source, 0);
        } catch (Exception e) { // ReflectiveOperationException is added since Android API 19.
            // This is not android device;
        }

        try {
            return java.util.Base64.getEncoder().encodeToString(source);
        } catch (Exception e) { // ReflectiveOperationException is added since Android API 19.
            throw new IllegalStateException(e);
        }
    }
}
