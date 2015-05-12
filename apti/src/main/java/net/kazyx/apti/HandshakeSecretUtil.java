package net.kazyx.apti;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class HandshakeSecretUtil {
    private HandshakeSecretUtil() {
    }

    /**
     * WebSocket GUID
     */
    private static final String UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /**
     * @return Newly created secret key.
     */
    static String newSecretKey() {
        byte[] nonce = new byte[16];
        RandomSource.random().nextBytes(nonce);
        return Base64.encoder().encode(nonce).trim();
    }

    /**
     * Scramble secret key.
     *
     * @param rawSecret Received Sec-WebSocket-Accept value.
     * @return Base64 encoded, SHA-1 hash of the secret key.
     */
    static String scrambleSecret(String rawSecret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((rawSecret + UUID).getBytes("UTF-8"));
            return Base64.encoder().encode(md.digest()).trim();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
