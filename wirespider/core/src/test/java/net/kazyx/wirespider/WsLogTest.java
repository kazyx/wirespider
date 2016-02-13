/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.WsLog;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class WsLogTest {
    private static final String TAG = WsLogTest.class.getSimpleName();

    public static class CustomWriterTest {
        @BeforeClass
        public static void setupClass() {
            WsLog.writer(new WsLog.Writer() {
                @Override
                public void v(String tag, String message) {
                    if (sLatch != null) {
                        sLatch.countDown();
                    }
                }

                @Override
                public void d(String tag, String message) {
                    if (sLatch != null) {
                        sLatch.countDown();
                    }
                }

                @Override
                public void e(String tag, String message) {
                    if (sLatch != null) {
                        sLatch.countDown();
                    }
                }

                @Override
                public void printStackTrace(String tag, Throwable th) {
                    if (sLatch != null) {
                        sLatch.countDown();
                    }
                }
            });
        }

        private static CountDownLatch sLatch;

        @Test
        public void logLevelVerbose() throws InterruptedException {
            WsLog.logLevel(WsLog.Level.VERBOSE);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        }

        @Test
        public void logLevelDebug() throws InterruptedException {
            WsLog.logLevel(WsLog.Level.DEBUG);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(1L));
        }

        @Test
        public void logLevelError() throws InterruptedException {
            WsLog.logLevel(WsLog.Level.ERROR);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(2L));
        }

        @Test
        public void logLevelSilent() throws InterruptedException {
            WsLog.logLevel(WsLog.Level.SILENT);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(4L));
        }

        @Test(expected = NullPointerException.class)
        public void nullLogLevel() {
            WsLog.logLevel(null);
        }

        private void callAllLogLevelsOnNewThread() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    WsLog.v(TAG, "verbose");
                    WsLog.d(TAG, "debug");
                    WsLog.e(TAG, "error");
                    WsLog.printStackTrace(TAG, new Exception("exception"));
                }
            }).start();
        }

        @Test(expected = NullPointerException.class)
        public void nullWriter() {
            WsLog.writer(null);
        }
    }

    public static class DefaultWriterTest {
        @BeforeClass
        public static void setupClass() {
            WsLog.writer(new WsLog.DefaultWriter());
        }

        @Test
        public void invokeDefaultWriterMethodsVerbose() {
            WsLog.logLevel(WsLog.Level.VERBOSE);
            WsLog.v(TAG, "verbose", "detail");
            WsLog.v(TAG, "verbose", 1);
            WsLog.d(TAG, "debug", "detail");
            WsLog.d(TAG, "debug", 1);
            WsLog.e(TAG, "error", "detail");
            WsLog.e(TAG, "error", 1);
            WsLog.printStackTrace(TAG, new Exception());
        }

        @Test
        public void invokeDefaultWriterMethodsSilent() {
            WsLog.logLevel(WsLog.Level.SILENT);
            WsLog.v(TAG, "verbose", "detail");
            WsLog.v(TAG, "verbose", 1);
            WsLog.d(TAG, "debug", "detail");
            WsLog.d(TAG, "debug", 1);
            WsLog.e(TAG, "error", "detail");
            WsLog.e(TAG, "error", 1);
            WsLog.printStackTrace(TAG, new Exception());
        }
    }
}
