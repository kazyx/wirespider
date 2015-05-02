package net.kazyx.apti;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class HandshakeSecretUtil {
    private HandshakeSecretUtil() {
    }

    private static final String UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    static String createNew() {
        byte[] nonce = new byte[16];
        for (int i = 0; i < 16; i++) {
            nonce[i] = (byte) (Math.random() * 256);
        }
        return Base64.getEncoder().encode(nonce).trim();
    }

    static String convertForValidation(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((secret + UUID).getBytes());
            return Base64.getEncoder().encode(md.digest()).trim();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
