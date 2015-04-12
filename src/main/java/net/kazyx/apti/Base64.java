package net.kazyx.apti;

public class Base64 {
    private Base64() {
    }

    private static Encoder sEncoder;

    /**
     * Set Base64 encoder instance.
     *
     * @param encoder Encoder.
     */
    public static void setEncoder(Encoder encoder) {
        sEncoder = encoder;
    }

    static Encoder getEncoder() {
        if (sEncoder == null) {
            throw new IllegalStateException("Base64.Encoder is not set yet.");
        }
        return sEncoder;
    }

    public interface Encoder {
        /**
         * Encode byte array to Base64 string.
         *
         * @param source byte array to be encoded.
         * @return Base64 string.
         */
        String encode(byte[] source);
    }
}