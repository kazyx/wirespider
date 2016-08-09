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
import net.kazyx.wirespider.extension.compression.PerMessageDeflate;
import net.kazyx.wirespider.util.Base64;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class ExtensionDeflateTest {
    public static class CompressorTest {
        private PerMessageDeflate mCompression;

        @Before
        public void setup() {
            mCompression = net.kazyx.wirespider.extension.compression.PerMessageDeflateCreator.create(0);
        }

        @Test
        public void compressDecompress() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                sb.append("TestMessage");
            }
            byte[] source = sb.toString().getBytes("UTF-8");
            ByteBuffer compressed = mCompression.compress(ByteBuffer.wrap(source));
            System.out.println("Compressed: " + source.length + " to " + compressed.remaining());

            ByteBuffer decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.capacity());
            assertThat(Arrays.equals(source, decompressed.array()), is(true));
        }

        @Test
        public void compressDecompress2() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                sb.append("TestMessage");
            }
            byte[] source = sb.toString().getBytes("UTF-8");
            ByteBuffer compressed = mCompression.compress(ByteBuffer.wrap(source));
            System.out.println("Compressed: " + source.length + " to " + compressed.remaining());

            ByteBuffer decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.capacity());
            assertThat(Arrays.equals(source, decompressed.array()), is(true));

            compressed = mCompression.compress(ByteBuffer.wrap(source));
            System.out.println("Compressed: " + source.length + " to " + compressed.remaining());

            decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.capacity());
            assertThat(Arrays.equals(source, decompressed.array()), is(true));
        }
    }

    public static class BuilderTest {
        @Test(expected = IllegalArgumentException.class)
        public void maxServerWindowBitsLow() {
            new DeflateRequest.Builder().setMaxServerWindowBits(7);
        }

        @Test(expected = IllegalArgumentException.class)
        public void maxServerWindowBitsHigh() {
            new DeflateRequest.Builder().setMaxServerWindowBits(16);
        }

        @Test
        public void maxServerWindowBitsInRange() {
            new DeflateRequest.Builder()
                    .setMaxServerWindowBits(8)
                    .setMaxServerWindowBits(9)
                    .setMaxServerWindowBits(10)
                    .setMaxServerWindowBits(11)
                    .setMaxServerWindowBits(12)
                    .setMaxServerWindowBits(13)
                    .setMaxServerWindowBits(14)
                    .setMaxServerWindowBits(15);
        }
    }

    public static class IntegrationDeflateTest {
        private static TestWebSocketServer server = new TestWebSocketServer(10000);

        @BeforeClass
        public static void setupClass() throws Exception {
            Base64.setEncoder(new Base64Encoder());
            server.registerExtension(TestWebSocketServer.Extension.DEFLATE);
            server.boot();
        }

        @AfterClass
        public static void teardownClass() throws Exception {
            server.shutdown();
        }

        @Test
        public void fixedTextCompressionWindow15() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            fixedTextCompressionByWindowSize(15, 4096);
        }

        @Test
        public void fixedTextCompressionWindow8() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            fixedTextCompressionByWindowSize(8, 4096);
        }

        @Test
        public void smallText() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            fixedTextCompressionByWindowSize(15, 1);
        }

        private void fixedTextCompressionByWindowSize(int windowSize, int msgSize) throws IOException, InterruptedException, ExecutionException, TimeoutException {
            final CustomLatch latch = new CustomLatch(1);
            final String data = TestUtil.fixedLengthFixedString(msgSize);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .setMaxServerWindowBits(windowSize)
                    .setCompressionThreshold(100)
                    .build();
            SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                    .setCloseHandler((code, reason) -> latch.unlockByFailure())
                    .setTextHandler(message -> {
                        if (data.equals(message)) {
                            latch.countDown();
                        } else {
                            System.out.println("Text message not matched");
                            latch.unlockByFailure();
                        }
                    })
                    .setExtensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .setMaxResponsePayloadSizeInBytes(msgSize * 5)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();

            try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
                assertThat(ws.extensions().size(), is(1));
                assertThat(ws.extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendTextMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                factory.destroy();
            }
        }

        @Test
        public void randomTextCompressionWindow15() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            randomTextCompressionByWindowSize(15);
        }

        @Test
        public void randomTextCompressionWindow8() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            randomTextCompressionByWindowSize(8);
        }

        private void randomTextCompressionByWindowSize(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
            final int MESSAGE_SIZE = 4096;
            final CustomLatch latch = new CustomLatch(1);
            final String data = TestUtil.fixedLengthRandomString(MESSAGE_SIZE);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .setMaxServerWindowBits(size)
                    .build();
            SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                    .setCloseHandler((code, reason) -> latch.unlockByFailure())
                    .setTextHandler(message -> {
                        if (data.equals(message)) {
                            latch.countDown();
                        } else {
                            System.out.println("Text message not matched");
                            latch.unlockByFailure();
                        }
                    })
                    .setExtensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .setMaxResponsePayloadSizeInBytes(MESSAGE_SIZE * 5)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();

            try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
                assertThat(ws.extensions().size(), is(1));
                assertThat(ws.extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendTextMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                factory.destroy();
            }
        }

        @Test
        public void fixedBinaryCompressionWindow8() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            fixedBinaryCompressionByWindowSize(8, 4096);
        }

        @Test
        public void fixedBinaryCompressionWindow15() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            fixedBinaryCompressionByWindowSize(15, 4096);
        }

        @Test
        public void smallBinary() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            fixedBinaryCompressionByWindowSize(15, 1);
        }

        private void fixedBinaryCompressionByWindowSize(int serverWindowSize, final int msgSize) throws ExecutionException, InterruptedException, TimeoutException, IOException {
            final CustomLatch latch = new CustomLatch(1);
            final byte[] data = TestUtil.fixedLengthFixedByteArray(msgSize);
            final byte[] copy = Arrays.copyOf(data, data.length);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .setMaxServerWindowBits(serverWindowSize)
                    .setCompressionThreshold(100)
                    .build();
            SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                    .setCloseHandler((code, reason) -> latch.unlockByFailure())
                    .setBinaryHandler(message -> {
                        if (Arrays.equals(copy, message)) {
                            latch.countDown();
                        } else {
                            System.out.println("Text message not matched");
                            latch.unlockByFailure();
                        }
                    })
                    .setExtensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .setMaxResponsePayloadSizeInBytes(msgSize * 5)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();

            try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
                assertThat(ws.handshake().extensions().size(), is(1));
                assertThat(ws.handshake().extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendBinaryMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                factory.destroy();
            }
        }

        @Test
        public void randomBinaryCompressionWindow8() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            randomBinaryCompressionByWindowSize(8);
        }

        @Test
        public void randomBinaryCompressionWindow15() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            randomBinaryCompressionByWindowSize(15);
        }

        private void randomBinaryCompressionByWindowSize(int size) throws ExecutionException, InterruptedException, TimeoutException, IOException {
            final int MESSAGE_SIZE = 4096;
            final CustomLatch latch = new CustomLatch(1);
            final byte[] data = TestUtil.fixedLengthRandomByteArray(MESSAGE_SIZE);
            final byte[] copy = Arrays.copyOf(data, data.length);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .setMaxServerWindowBits(size)
                    .build();
            SessionRequest req = new SessionRequest.Builder(URI.create("ws://127.0.0.1:10000"))
                    .setCloseHandler((code, reason) -> latch.unlockByFailure())
                    .setBinaryHandler(message -> {
                        if (Arrays.equals(copy, message)) {
                            latch.countDown();
                        } else {
                            System.out.println("Text message not matched");
                            latch.unlockByFailure();
                        }
                    })
                    .setExtensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .setMaxResponsePayloadSizeInBytes(MESSAGE_SIZE * 5)
                    .build();

            WebSocketFactory factory = new WebSocketFactory();

            try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
                assertThat(ws.handshake().extensions().size(), is(1));
                assertThat(ws.handshake().extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendBinaryMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                factory.destroy();
            }
        }
    }

    public static class CompressionStrategyTest {
        private static final int SIZE_BASE = 200;

        private PerMessageDeflate mCompression;

        @Before
        public void setup() {
            mCompression = net.kazyx.wirespider.extension.compression.PerMessageDeflateCreator.create(SIZE_BASE);
        }

        @Test(expected = IOException.class)
        public void dataSizeSmallerThanCompressionMinRange() throws IOException {
            final byte[] original = TestUtil.fixedLengthFixedByteArray(SIZE_BASE - 1);
            mCompression.compress(ByteBuffer.wrap(original)).array();
        }

        @Test
        public void dataSizeEqualsCompressionMinRange() throws IOException {
            final byte[] original = TestUtil.fixedLengthFixedByteArray(SIZE_BASE);
            final byte[] copy = Arrays.copyOf(original, original.length);
            byte[] compressed = mCompression.compress(ByteBuffer.wrap(original)).array();
            assertThat(Arrays.equals(copy, compressed), is(false));
        }

        @Test
        public void dataSizeLargerThanCompressionMinRange() throws IOException {
            final byte[] original = TestUtil.fixedLengthFixedByteArray(SIZE_BASE + 1);
            final byte[] copy = Arrays.copyOf(original, original.length);
            byte[] compressed = mCompression.compress(ByteBuffer.wrap(original)).array();
            assertThat(Arrays.equals(copy, compressed), is(false));
        }

        @Test(expected = IOException.class)
        public void requestBuilder() throws IOException {
            DeflateRequest req = new DeflateRequest.Builder()
                    .setCompressionThreshold(2)
                    .build();
            PerMessageDeflate deflate = ((PerMessageDeflate) req.extension());

            byte[] one = {(byte) 0x11};
            assertThat(Arrays.equals(deflate.compress(ByteBuffer.wrap(one)).array(), one), is(true));
        }
    }
}
