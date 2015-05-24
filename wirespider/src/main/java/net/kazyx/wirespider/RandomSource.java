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
    public static void seed(long seed) {
        sRandom.setSeed(seed);
    }

    private static final Random sRandom = new Random();

    static Random random() {
        return sRandom;
    }
}
