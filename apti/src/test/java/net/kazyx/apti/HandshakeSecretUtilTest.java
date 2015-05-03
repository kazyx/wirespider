package net.kazyx.apti;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HandshakeSecretUtilTest {
    @BeforeClass
    public static void setupClass() throws Exception {
        Base64.setEncoder(new Base64.Encoder() {
            @Override
            public String encode(byte[] source) {
                return org.apache.commons.codec.binary.Base64.encodeBase64String(source);
            }
        });
    }

    @Test
    public void secretNeverSame() {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            String secret = HandshakeSecretUtil.newSecretKey();
            assertThat(set.add(secret), is(true));
        }
    }

    @Test
    public void scrambleRFCSampleValue() {
        String sampleSecret = "dGhlIHNhbXBsZSBub25jZQ==";
        String sampleScrambled = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
        assertThat(HandshakeSecretUtil.scrambleSecret(sampleSecret), is(sampleScrambled));
    }
}
