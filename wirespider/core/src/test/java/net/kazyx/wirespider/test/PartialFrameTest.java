/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.test;

import net.kazyx.wirespider.PartialMessageWriter;
import net.kazyx.wirespider.SessionRequest;
import net.kazyx.wirespider.WebSocket;
import net.kazyx.wirespider.WebSocketFactory;
import net.kazyx.wirespider.util.Base64;
import net.kazyx.wirespider.util.BinaryUtil;
import net.kazyx.wirespider.util.IOUtil;
import net.kazyx.wirespider.util.WsLog;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PartialFrameTest {
    private static TestWebSocketServer server = new TestWebSocketServer(10000);

    @BeforeClass
    public static void setupClass() throws Exception {
        WsLog.logLevel(WsLog.Level.VERBOSE);
        Base64.setEncoder(new Base64Encoder());
        server.boot();
    }

    @AfterClass
    public static void teardownClass() throws Exception {
        server.shutdown();
    }

    private CustomLatch mLatch;
    private WebSocketFactory mFactory;
    private WebSocket mWs;
    private PartialMessageWriter mWriter;

    @Before
    public void setup() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setBinaryHandler(message -> mLatch.countDown())
                .build();

        mLatch = new CustomLatch(1);
        mWriter = null;
        mFactory = new WebSocketFactory();
        mWs = mFactory.openAsync(req).get(1000, TimeUnit.MILLISECONDS);
    }

    @After
    public void tearDown() {
        IOUtil.close(mWs);
        IOUtil.close(mWriter);
        mFactory.destroy();
    }

    @Test
    public void checkEchoPartialText() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        final String first = "hello1";
        final String second = "hello2";

        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setTextHandler(message -> {
                    System.out.println("onBinaryMessage: " + message);
                    if ((first + second).equals(message)) {
                        latch.countDown();
                    } else {
                        latch.unlockByFailure();
                    }
                })
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            try (PartialMessageWriter writer = ws.newPartialMessageWriter()) {
                writer.sendPartialFrameAsync(first, false);
                writer.sendPartialFrameAsync(second, true);
            }
            assertThat(latch.await(1000, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void checkEchoNonPartialText() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        final String first = "hello";

        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setTextHandler(message -> {
                    if (first.equals(message)) {
                        latch.countDown();
                    } else {
                        latch.unlockByFailure();
                    }
                })
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            try (PartialMessageWriter writer = ws.newPartialMessageWriter()) {
                writer.sendPartialFrameAsync(first, true);
            }
            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void checkEchoPartialBinaries() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        byte[] first = {0x12, 0x34, 0x56, 0x78, (byte) 0x9a};
        byte[] second = {(byte) 0xbc, (byte) 0xde, (byte) 0xf0, 0x12, 0x34};
        final byte[] whole = new byte[first.length + second.length];
        System.arraycopy(first, 0, whole, 0, first.length);
        System.arraycopy(second, 0, whole, first.length, second.length);

        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setBinaryHandler(message -> {
                    System.out.println("onBinaryMessage: " + BinaryUtil.toHex(message));
                    if (Arrays.equals(whole, message)) {
                        latch.countDown();
                    } else {
                        latch.unlockByFailure();
                    }
                })
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            try (PartialMessageWriter writer = ws.newPartialMessageWriter()) {
                writer.sendPartialFrameAsync(first, false);
                writer.sendPartialFrameAsync(second, true);
            }
            assertThat(latch.await(1000, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void checkEchoNonPartialBinaries() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        byte[] first = {0x12, 0x34, 0x56, 0x78, (byte) 0x9a};
        final byte[] whole = Arrays.copyOf(first, first.length);

        SessionRequest req = new SessionRequest.Builder(URI.create("ws://localhost:10000"))
                .setBinaryHandler(message -> {
                    if (Arrays.equals(whole, message)) {
                        latch.countDown();
                    } else {
                        latch.unlockByFailure();
                    }
                })
                .build();

        WebSocketFactory factory = new WebSocketFactory();

        try (WebSocket ws = factory.openAsync(req).get(1000, TimeUnit.MILLISECONDS)) {
            try (PartialMessageWriter writer = ws.newPartialMessageWriter()) {
                writer.sendPartialFrameAsync(first, true);
            }
            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            factory.destroy();
        }
    }

    @Test
    public void getDuplicateWriterOnOtherThread() throws InterruptedException {
        final WebSocket copy = mWs;
        mWriter = mWs.newPartialMessageWriter();
        new Thread(() -> {
            PartialMessageWriter writer = null;
            try {
                writer = copy.newPartialMessageWriter();
                mLatch.unlockByFailure();
            } catch (IllegalStateException e) {
                mLatch.countDown();
            } finally {
                IOUtil.close(writer);
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }

    @Test(expected = IllegalStateException.class)
    public void getDuplicateWriterOnSameThread() {
        PartialMessageWriter writer2 = null;
        try {
            mWriter = mWs.newPartialMessageWriter();
            writer2 = mWs.newPartialMessageWriter();
        } finally {
            IOUtil.close(writer2);
        }
    }

    @Test
    public void writerLocksOtherTextFramesOnOtherThread() throws InterruptedException {
        final WebSocket copy = mWs;
        mWriter = mWs.newPartialMessageWriter();
        new Thread(() -> {
            try {
                copy.sendTextMessageAsync("error");
                mLatch.unlockByFailure();
            } catch (IllegalStateException e) {
                mLatch.countDown();
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }

    @Test(expected = IllegalStateException.class)
    public void writerLocksOtherTextFramesOnSameThread() {
        mWriter = mWs.newPartialMessageWriter();
        mWs.sendTextMessageAsync("error");
    }

    @Test
    public void writerLocksOtherBinaryFramesOnOtherThread() throws InterruptedException {
        final WebSocket copy = mWs;
        mWriter = mWs.newPartialMessageWriter();
        new Thread(() -> {
            try {
                copy.sendBinaryMessageAsync(new byte[0x00]);
                mLatch.unlockByFailure();
            } catch (IllegalStateException e) {
                mLatch.countDown();
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }

    @Test(expected = IllegalStateException.class)
    public void writerLocksOtherBinaryFramesOnSameThread() {
        mWriter = mWs.newPartialMessageWriter();
        mWs.sendBinaryMessageAsync(new byte[0x00]);
    }

    @Test
    public void controlFrameIsNotLockedOnOtherThread() throws InterruptedException {
        final WebSocket copy = mWs;
        mWriter = mWs.newPartialMessageWriter();
        new Thread(() -> {
            copy.sendPingAsync("ping");
            mLatch.countDown();
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void controlFrameIsNotLockedOnSameThread() {
        mWriter = mWs.newPartialMessageWriter();
        mWs.sendPingAsync("ping");
    }

    @Test
    public void closingWriterUnlocks() {
        mWriter = mWs.newPartialMessageWriter();
        IOUtil.close(mWriter);
        mWriter = mWs.newPartialMessageWriter();
    }

    @Test(expected = IllegalStateException.class)
    public void sendFinalFrameDoesNotUnlockBinary() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
        mWs.sendTextMessageAsync("hello");
    }

    @Test(expected = IllegalStateException.class)
    public void sendFinalFrameDoesNotUnlockText() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync("Hello", true);
        mWs.sendTextMessageAsync("hello");
    }

    @Test
    public void reuseNonClosedWriterBinary() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
    }

    @Test
    public void reuseNonClosedWriterText() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync("Hello", true);
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync("Hello", true);
    }

    @Test(expected = IllegalStateException.class)
    public void typeConflict() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync("Hello", false);
    }

    @Test(expected = IllegalStateException.class)
    public void typeConflictReverse() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
    }

    @Test
    public void sendFinalFrameClearsDataType() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync("Hello", true);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
    }

    @Test(expected = IOException.class)
    public void closedWriterThrowsIOExceptionBinary() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        IOUtil.close(mWriter);
        byte[] first = {0x12, 0x34, 0x56, 0x78, (byte) 0x9a};
        mWriter.sendPartialFrameAsync(first, false);
    }

    @Test(expected = IOException.class)
    public void closedWriterThrowsIOExceptionText() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        IOUtil.close(mWriter);
        mWriter.sendPartialFrameAsync("hello", false);
    }

    @Test(expected = IOException.class)
    public void closeClosedWriter() throws IOException {
        mWriter = mWs.newPartialMessageWriter();
        IOUtil.close(mWriter);
        mWriter.close();
    }

    @Test
    public void closeOnOtherThread() throws InterruptedException {
        mWriter = mWs.newPartialMessageWriter();
        new Thread(() -> {
            try {
                mWriter.close();
                mLatch.unlockByFailure();
            } catch (IOException e) {
                mLatch.countDown();
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }
}
