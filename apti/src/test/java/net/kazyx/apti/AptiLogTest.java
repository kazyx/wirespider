package net.kazyx.apti;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(Enclosed.class)
public class AptiLogTest {
    private static final String TAG = AptiLogTest.class.getSimpleName();

    public static class CustomWriterTest {
        @BeforeClass
        public static void setupClass() {
            AptiLog.writer(new AptiLog.Writer() {
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
            AptiLog.logLevel(AptiLog.Level.VERBOSE);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        }

        @Test
        public void logLevelDebug() throws InterruptedException {
            AptiLog.logLevel(AptiLog.Level.DEBUG);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(1L));
        }

        @Test
        public void logLevelError() throws InterruptedException {
            AptiLog.logLevel(AptiLog.Level.ERROR);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(2L));
        }

        @Test
        public void logLevelSilent() throws InterruptedException {
            AptiLog.logLevel(AptiLog.Level.SILENT);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(4L));
        }

        @Test(expected = NullPointerException.class)
        public void nullLogLevel() {
            AptiLog.logLevel(null);
        }

        private void callAllLogLevelsOnNewThread() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    AptiLog.v(TAG, "verbose");
                    AptiLog.d(TAG, "debug");
                    AptiLog.e(TAG, "error");
                    AptiLog.printStackTrace(TAG, new Exception("exception"));
                }
            }).start();
        }

        @Test(expected = NullPointerException.class)
        public void nullWriter() {
            AptiLog.writer(null);
        }
    }

    public static class DefaultWriterTest {
        @BeforeClass
        public static void setupClass() {
            AptiLog.writer(new AptiLog.DefaultWriter());
        }

        @Test
        public void invokeDefaultWriterMethodsVerbose() {
            AptiLog.logLevel(AptiLog.Level.VERBOSE);
            AptiLog.v(TAG, "verbose", "detail");
            AptiLog.v(TAG, "verbose", 1);
            AptiLog.d(TAG, "debug", "detail");
            AptiLog.d(TAG, "debug", 1);
            AptiLog.e(TAG, "error", "detail");
            AptiLog.e(TAG, "error", 1);
            AptiLog.printStackTrace(TAG, new Exception());
        }

        @Test
        public void invokeDefaultWriterMethodsSilent() {
            AptiLog.logLevel(AptiLog.Level.SILENT);
            AptiLog.v(TAG, "verbose", "detail");
            AptiLog.v(TAG, "verbose", 1);
            AptiLog.d(TAG, "debug", "detail");
            AptiLog.d(TAG, "debug", 1);
            AptiLog.e(TAG, "error", "detail");
            AptiLog.e(TAG, "error", 1);
            AptiLog.printStackTrace(TAG, new Exception());
        }
    }
}
