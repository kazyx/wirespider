/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

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

    CustomLatch mLatch;
    WebSocketFactory mFactory;
    WebSocket mWs;
    PartialMessageWriter mWriter;

    @Before
    public void setup() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onBinaryMessage(byte[] message) {
                mLatch.countDown();
            }
        }).build();

        mLatch = new CustomLatch(1);
        mWriter = null;
        mFactory = new WebSocketFactory();
        mWs = mFactory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);
    }

    @After
    public void tearDown() {
        if (mWs != null) {
            mWs.closeNow();
        }
        IOUtil.close(mWriter);
        mFactory.destroy();
    }

    @Test
    public void checkEchoPartialText() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        final String first = "hello1";
        final String second = "hello2";

        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onTextMessage(String message) {
                System.out.println("onBinaryMessage: " + message);
                if ((first + second).equals(message)) {
                    latch.countDown();
                } else {
                    latch.unlockByFailure();
                }
            }
        }).build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);

            PartialMessageWriter writer = ws.getPartialMessageWriter();
            writer.sendPartialFrameAsync(first, false);
            writer.sendPartialFrameAsync(second, true);

            assertThat(latch.await(1000, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void checkEchoNonPartialText() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        final String first = "hello";

        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onTextMessage(String message) {
                if (first.equals(message)) {
                    latch.countDown();
                } else {
                    latch.unlockByFailure();
                }
            }
        }).build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        PartialMessageWriter writer = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);

            writer = ws.getPartialMessageWriter();
            writer.sendPartialFrameAsync(first, true);

            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            IOUtil.close(writer);
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

        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onBinaryMessage(byte[] message) {
                System.out.println("onBinaryMessage: " + BinaryUtil.toHex(message));
                if (Arrays.equals(whole, message)) {
                    latch.countDown();
                } else {
                    latch.unlockByFailure();
                }
            }
        }).build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);

            PartialMessageWriter writer = ws.getPartialMessageWriter();
            writer.sendPartialFrameAsync(first, false);
            writer.sendPartialFrameAsync(second, true);

            assertThat(latch.await(1000, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            factory.destroy();
        }
    }

    @Test
    public void checkEchoNonPartialBinaries() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final CustomLatch latch = new CustomLatch(1);
        byte[] first = {0x12, 0x34, 0x56, 0x78, (byte) 0x9a};
        final byte[] whole = Arrays.copyOf(first, first.length);

        SessionRequest seed = new SessionRequest.Builder(URI.create("ws://localhost:10000"), new SilentEventHandler() {
            @Override
            public void onBinaryMessage(byte[] message) {
                if (Arrays.equals(whole, message)) {
                    latch.countDown();
                } else {
                    latch.unlockByFailure();
                }
            }
        }).build();

        WebSocketFactory factory = new WebSocketFactory();
        WebSocket ws = null;
        PartialMessageWriter writer = null;
        try {
            ws = factory.openAsync(seed).get(1000, TimeUnit.MILLISECONDS);

            writer = ws.getPartialMessageWriter();
            writer.sendPartialFrameAsync(first, true);

            assertThat(latch.await(500, TimeUnit.MILLISECONDS), is(true));
        } finally {
            if (ws != null) {
                ws.closeNow();
            }
            IOUtil.close(writer);
            factory.destroy();
        }
    }

    @Test
    public void getDuplicateWriterOnOtherThread() throws InterruptedException {
        final WebSocket copy = mWs;
        mWriter = mWs.getPartialMessageWriter();
        new Thread(new Runnable() {
            @Override
            public void run() {
                PartialMessageWriter writer = null;
                try {
                    writer = copy.getPartialMessageWriter();
                    mLatch.unlockByFailure();
                } catch (IllegalStateException e) {
                    mLatch.countDown();
                } finally {
                    IOUtil.close(writer);
                }
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }

    @Test(expected = IllegalStateException.class)
    public void getDuplicateWriterOnSameThread() {
        PartialMessageWriter writer2 = null;
        try {
            mWriter = mWs.getPartialMessageWriter();
            writer2 = mWs.getPartialMessageWriter();
        } finally {
            IOUtil.close(writer2);
        }
    }

    @Test
    public void writerLocksOtherTextFramesOnOtherThread() throws InterruptedException {
        final WebSocket copy = mWs;
        mWriter = mWs.getPartialMessageWriter();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    copy.sendTextMessageAsync("error");
                    mLatch.unlockByFailure();
                } catch (IllegalStateException e) {
                    mLatch.countDown();
                }
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }

    @Test(expected = IllegalStateException.class)
    public void writerLocksOtherTextFramesOnSameThread() {
        mWriter = mWs.getPartialMessageWriter();
        mWs.sendTextMessageAsync("error");
    }

    @Test
    public void writerLocksOtherBinaryFramesOnOtherThread() throws InterruptedException {
        final WebSocket copy = mWs;
        mWriter = mWs.getPartialMessageWriter();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    copy.sendBinaryMessageAsync(new byte[0x00]);
                    mLatch.unlockByFailure();
                } catch (IllegalStateException e) {
                    mLatch.countDown();
                }
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }

    @Test(expected = IllegalStateException.class)
    public void writerLocksOtherBinaryFramesOnSameThread() {
        mWriter = mWs.getPartialMessageWriter();
        mWs.sendBinaryMessageAsync(new byte[0x00]);
    }

    @Test
    public void controlFrameIsNotLockedOnOtherThread() throws InterruptedException {
        final WebSocket copy = mWs;
        mWriter = mWs.getPartialMessageWriter();
        new Thread(new Runnable() {
            @Override
            public void run() {
                copy.sendPingAsync("ping");
                mLatch.countDown();
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }

    @Test
    public void controlFrameIsNotLockedOnSameThread() {
        mWriter = mWs.getPartialMessageWriter();
        mWs.sendPingAsync("ping");
    }

    @Test
    public void closingWriterUnlocks() {
        mWriter = mWs.getPartialMessageWriter();
        IOUtil.close(mWriter);
        mWriter = mWs.getPartialMessageWriter();
    }

    @Test(expected = IllegalStateException.class)
    public void sendFinalFrameDoesNotUnlockBinary() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
        mWs.sendTextMessageAsync("hello");
    }

    @Test(expected = IllegalStateException.class)
    public void sendFinalFrameDoesNotUnlockText() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync("Hello", true);
        mWs.sendTextMessageAsync("hello");
    }

    @Test
    public void reuseNonClosedWriterBinary() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
    }

    @Test
    public void reuseNonClosedWriterText() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync("Hello", true);
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync("Hello", true);
    }

    @Test(expected = IllegalStateException.class)
    public void typeConflict() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync("Hello", false);
    }

    @Test(expected = IllegalStateException.class)
    public void typeConflictReverse() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
    }

    @Test
    public void sendFinalFrameClearsDataType() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, false);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
        mWriter.sendPartialFrameAsync("Hello", false);
        mWriter.sendPartialFrameAsync("Hello", true);
        mWriter.sendPartialFrameAsync(new byte[]{0x00}, true);
    }

    @Test(expected = IOException.class)
    public void closedWriterThrowsIOExceptionBinary() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        IOUtil.close(mWriter);
        byte[] first = {0x12, 0x34, 0x56, 0x78, (byte) 0x9a};
        mWriter.sendPartialFrameAsync(first, false);
    }

    @Test(expected = IOException.class)
    public void closedWriterThrowsIOExceptionText() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        IOUtil.close(mWriter);
        mWriter.sendPartialFrameAsync("hello", false);
    }

    @Test(expected = IOException.class)
    public void closeClosedWriter() throws IOException {
        mWriter = mWs.getPartialMessageWriter();
        IOUtil.close(mWriter);
        mWriter.close();
    }

    @Test
    public void closeOnOtherThread() throws InterruptedException {
        mWriter = mWs.getPartialMessageWriter();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mWriter.close();
                    mLatch.unlockByFailure();
                } catch (IOException e) {
                    mLatch.countDown();
                }
            }
        }).start();
        assertThat(mLatch.await(500, TimeUnit.MILLISECONDS), is(true));
    }
}
