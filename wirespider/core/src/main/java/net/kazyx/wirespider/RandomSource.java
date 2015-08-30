/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import java.util.Random;

public class RandomSource {
    private RandomSource() {
    }

    /**
     * Set initial seed of {@link Random} to be used for generating mask and handshake secret.
     *
     * @param seed Initial seed.
     */
    public static void setSeed(long seed) {
        sRandom.setSeed(seed);
    }

    private static final Random sRandom = new Random();

    static Random random() {
        return sRandom;
    }
}
