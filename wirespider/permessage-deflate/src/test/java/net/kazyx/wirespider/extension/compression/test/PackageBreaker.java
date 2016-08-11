/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.extension.compression.test;

import net.kazyx.wirespider.extension.compression.PerMessageDeflate;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

class PackageBreaker {
    static PerMessageDeflate newPerMessageDeflate(int threshold) {
        try {
            Constructor<PerMessageDeflate> constructor = PerMessageDeflate.class.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(threshold);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
