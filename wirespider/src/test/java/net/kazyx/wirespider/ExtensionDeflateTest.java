package net.kazyx.wirespider;

import net.kazyx.wirespider.extension.ExtensionRequest;
import net.kazyx.wirespider.extension.compression.DeflateRequest;
import net.kazyx.wirespider.extension.compression.PerMessageDeflate;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.common.extensions.compress.PerMessageDeflateExtension;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@RunWith(Enclosed.class)
public class ExtensionDeflateTest {

    public static class CompressorTest {
        private PerMessageDeflate mCompression;

        @Before
        public void setup() {
            mCompression = new PerMessageDeflate();
        }

        @Test
        public void compressDecompress() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                sb.append("TestMessage");
            }
            byte[] source = sb.toString().getBytes("UTF-8");
            byte[] compressed = mCompression.compress(source);
            System.out.println("Compressed: " + source.length + " to " + compressed.length);

            byte[] decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.length);
            assertThat(Arrays.equals(source, decompressed), is(true));
        }

        @Test
        public void compressDecompress2() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100000; i++) {
                sb.append("TestMessage");
            }
            byte[] source = sb.toString().getBytes("UTF-8");
            byte[] compressed = mCompression.compress(source);
            System.out.println("Compressed: " + source.length + " to " + compressed.length);

            byte[] decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.length);

            compressed = mCompression.compress(source);
            System.out.println("Compressed: " + source.length + " to " + compressed.length);

            decompressed = mCompression.decompress(compressed);

            System.out.println("Decompressed: " + decompressed.length);
        }
    }

    public static class BuilderTest {

        @Test(expected = IllegalArgumentException.class)
        public void maxClientWindowBitsLow() {
            new DeflateRequest.Builder().maxClientWindowBits(7);
        }

        @Test(expected = IllegalArgumentException.class)
        public void maxClientWindowBitsHigh() {
            new DeflateRequest.Builder().maxClientWindowBits(16);
        }

        @Test(expected = IllegalArgumentException.class)
        public void maxServerWindowBitsLow() {
            new DeflateRequest.Builder().maxServerWindowBits(7);
        }

        @Test(expected = IllegalArgumentException.class)
        public void maxServerWindowBitsHigh() {
            new DeflateRequest.Builder().maxServerWindowBits(16);
        }

        @Test
        public void maxClientWindowBitsInRange() {
            new DeflateRequest.Builder()
                    .maxClientWindowBits(8)
                    .maxClientWindowBits(9)
                    .maxClientWindowBits(10)
                    .maxClientWindowBits(11)
                    .maxClientWindowBits(12)
                    .maxClientWindowBits(13)
                    .maxClientWindowBits(14)
                    .maxClientWindowBits(15);
        }

        @Test
        public void maxServerWindowBitsInRange() {
            new DeflateRequest.Builder()
                    .maxServerWindowBits(8)
                    .maxServerWindowBits(9)
                    .maxServerWindowBits(10)
                    .maxServerWindowBits(11)
                    .maxServerWindowBits(12)
                    .maxServerWindowBits(13)
                    .maxServerWindowBits(14)
                    .maxServerWindowBits(15);
        }
    }

    public static class IntegrationDeflateTest {
        private static Server server = new Server(10000);

        @BeforeClass
        public static void setupClass() throws Exception {
            RandomSource.seed(0x12345678);
            Log.logLevel(Log.Level.VERBOSE);
            WebSocketServlet servlet = new WebSocketServlet() {
                @Override
                public void configure(WebSocketServletFactory factory) {
                    factory.setCreator(new WebSocketCreator() {
                        @Override
                        public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
                            if (req.getHeader(JettyWebSocketServlet.REJECT_KEY) != null) {
                                System.out.println("JettyWebSocket: Reject upgrade");
                                return null;
                            } else {
                                return new JettyWebSocketServlet();
                            }
                        }
                    });
                    factory.getExtensionFactory().register("permessage-deflate", PerMessageDeflateExtension.class);
                }
            };

            ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
            handler.addServlet(new ServletHolder(servlet), "/");
            server.setHandler(handler);

            Base64.encoder(new Base64.Encoder() {
                @Override
                public String encode(byte[] source) {
                    return org.apache.commons.codec.binary.Base64.encodeBase64String(source);
                }
            });

            mStartLatch = new CountDownLatch(1);
            mEndLatch = new CountDownLatch(1);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        server.start();
                        System.out.println("Server started");
                        mStartLatch.countDown();
                        server.join();
                        System.out.println("Server finished");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        mEndLatch.countDown();
                    }
                }
            }).start();

            mStartLatch.await();
        }

        private static CountDownLatch mStartLatch;
        private static CountDownLatch mEndLatch;

        @AfterClass
        public static void teardownClass() throws Exception {
            server.stop();
            System.out.println("Server stopped");
            mEndLatch.await();
        }

        @Test
        public void textCompressionWindow15() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            textCompressionByWindowSize(15);
        }

        @Test
        public void textCompressionWindow8() throws ExecutionException, InterruptedException, TimeoutException, IOException {
            textCompressionByWindowSize(8);
        }

        private void textCompressionByWindowSize(int size) throws IOException, InterruptedException, ExecutionException, TimeoutException {
            final int MESSAGE_SIZE = 4096;
            final CustomLatch latch = new CustomLatch(1);
            final String data = TestUtil.fixedLengthString(MESSAGE_SIZE);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .maxClientWindowBits(size)
                    .maxServerWindowBits(size)
                    .build();
            WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
                @Override
                public void onClosed(int code, String reason) {
                    latch.unlockByFailure();
                }

                @Override
                public void onTextMessage(String message) {
                    if (data.equals(message)) {
                        latch.countDown();
                    } else {
                        System.out.println("Text message not matched");
                        latch.unlockByFailure();
                    }
                }
            }).extensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .maxResponsePayloadSizeInBytes(MESSAGE_SIZE - 1)
                    .build();

            WebSocketClientFactory factory = new WebSocketClientFactory();
            WebSocket ws = null;
            try {
                Future<WebSocket> future = factory.openAsync(seed);
                ws = future.get(1000, TimeUnit.MILLISECONDS);
                assertThat(ws.handshake().extensions().size(), is(1));
                assertThat(ws.handshake().extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendTextMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                if (ws != null) {
                    ws.closeNow();
                }
                factory.destroy();
            }
        }

        @Test
        public void binaryCompressionWindow8() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            binaryCompressionByWindowSize(8);
        }

        @Test
        public void binaryCompressionWindow15() throws InterruptedException, ExecutionException, TimeoutException, IOException {
            binaryCompressionByWindowSize(15);
        }

        private void binaryCompressionByWindowSize(int size) throws ExecutionException, InterruptedException, TimeoutException, IOException {
            final int MESSAGE_SIZE = 4096;
            final CustomLatch latch = new CustomLatch(1);
            final byte[] data = TestUtil.fixedLengthByteArray(MESSAGE_SIZE);
            final byte[] copy = Arrays.copyOf(data, data.length);
            DeflateRequest extReq = new DeflateRequest.Builder()
                    .maxClientWindowBits(size)
                    .maxServerWindowBits(size)
                    .build();
            WebSocketSeed seed = new WebSocketSeed.Builder(URI.create("ws://127.0.0.1:10000"), new SilentEventHandler() {
                @Override
                public void onClosed(int code, String reason) {
                    latch.unlockByFailure();
                }

                @Override
                public void onBinaryMessage(byte[] message) {
                    if (Arrays.equals(copy, message)) {
                        latch.countDown();
                    } else {
                        System.out.println("Text message not matched");
                        latch.unlockByFailure();
                    }
                }
            }).extensions(Collections.<ExtensionRequest>singletonList(extReq))
                    .maxResponsePayloadSizeInBytes(MESSAGE_SIZE * 2)
                    .build();

            WebSocketClientFactory factory = new WebSocketClientFactory();
            WebSocket ws = null;
            try {
                Future<WebSocket> future = factory.openAsync(seed);
                ws = future.get(1000, TimeUnit.MILLISECONDS);
                assertThat(ws.handshake().extensions().size(), is(1));
                assertThat(ws.handshake().extensions().get(0), instanceOf(PerMessageDeflate.class));
                ws.sendBinaryMessageAsync(data);
                assertThat(latch.await(10000, TimeUnit.MILLISECONDS), is(true));
                assertThat(latch.isUnlockedByFailure(), is(false));
            } finally {
                if (ws != null) {
                    ws.closeNow();
                }
                factory.destroy();
            }
        }

    }
}
