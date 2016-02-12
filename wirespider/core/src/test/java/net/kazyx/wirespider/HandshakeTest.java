/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.extension.compression.DeflateRequest;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HandshakeTest {
    private static final URI DUMMY_URI = URI.create("ws://127.0.0.1:10000/");
    private Rfc6455Handshake mHandshake;

    @BeforeClass
    public static void setupClass() {
        Base64.setEncoder(new Base64Encoder());
    }

    @Before
    public void setup() throws IOException {
        mHandshake = new Rfc6455Handshake(new SocketChannelWriter() {
            @Override
            public void writeAsync(ByteBuffer data) {
            }

            @Override
            public void writeAsync(ByteBuffer data, boolean calledOnSelectorThread) {
            }
        }, true);
    }

    @Test(expected = HandshakeFailureException.class)
    public void invalidHttpVersion() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.0 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void invalidStatusCode() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 OK Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void invalidStatusLine() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1\r\n101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void noStatusLine() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = BufferUnsatisfiedException.class)
    public void unsatisfiedHeader() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        String statusLine = "HTTP/1.1 101 Switching Protocols\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(statusLine));
    }

    @Test(expected = HandshakeFailureException.class)
    public void noUpgradeHeader() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void noConnectionHeader() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void noSecHeader() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void invalidUpgradeHeader() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: hogehoge\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void invalidConnectionHeader() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: close\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void invalidSecHeader() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test
    public void normalSuccessUpgrade() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test
    public void includeTripleSeparatedHeaderWithWS() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n"
                + "dummy: val1\r\n"
                + " val2\r\n"
                + " val3\r\n"
                + "\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test
    public void includeTripleSeparatedHeaderWithTab() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n"
                + "dummy: val1\r\n"
                + "\tval2\r\n"
                + "\tval3\r\n"
                + "\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test
    public void duplicatedHeaders() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n"
                + "dummy: val1\r\n"
                + "dummy: val2\r\n"
                + "\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void leadingLineDoesNotHaveHeaderName() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "dummy\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void leadingLineStartsWithWS() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + " dummy: val\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void leadingLineStartsWithTab() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "\tdummy: val\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = HandshakeFailureException.class)
    public void noHeader() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void upgradeRequestByServer() throws IOException {
        // mHandshake = new Rfc6455Handshake(new SocketChannelProxy(new SocketEngine(SelectorProvider.provider()), new SilentListener()), false);
        mHandshake = new Rfc6455Handshake(new SocketChannelWriter() {
            @Override
            public void writeAsync(ByteBuffer data) {
            }

            @Override
            public void writeAsync(ByteBuffer data, boolean calledOnSelectorThread) {
            }
        }, false);
        mHandshake.tryUpgrade(DUMMY_URI, null);
    }

    @Test
    public void multipleProtocolHeaders() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        dummySendUpgradeRequest(mHandshake);
        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Protocol: protocol1\r\n"
                + "Sec-WebSocket-Protocol: protocol2\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
        assertThat(mHandshake.protocol(), is("protocol1")); // We use the first one.
    }

    @Test(expected = HandshakeFailureException.class)
    public void invalidExtensionResponse() throws IOException, BufferUnsatisfiedException, HandshakeFailureException {
        List<ExtensionRequest> exReq = new ArrayList<>();
        exReq.add(new DeflateRequest.Builder().build());
        try {
            mHandshake.tryUpgrade(DUMMY_URI, new SessionRequest.Builder(DUMMY_URI, new SilentEventHandler()).setExtensions(exReq).build());
        } catch (NullPointerException e) {
            // Ignore
        }

        String secret = getSecret(mHandshake);
        String header = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Extensions: permessage-deflate;dummy-value;\r\n"
                + "Sec-WebSocket-Accept: " + HandshakeSecretUtil.scrambleSecret(secret) + "\r\n\r\n";
        mHandshake.onHandshakeResponse(TestUtil.asByteBuffer(header));
    }

    private static String getSecret(Rfc6455Handshake handshake) {
        try {
            Field f = Rfc6455Handshake.class.getDeclaredField("mSecret");
            f.setAccessible(true);
            return (String) f.get(handshake);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void dummySendUpgradeRequest(Handshake handshake) {
        try {
            handshake.tryUpgrade(DUMMY_URI, null);
        } catch (NullPointerException e) {
            // Ignore
        }
    }
}
