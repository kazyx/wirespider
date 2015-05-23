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
public class LogTest {
    private static final String TAG = LogTest.class.getSimpleName();

    public static class CustomWriterTest {
        @BeforeClass
        public static void setupClass() {
            Log.writer(new Log.Writer() {
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
            Log.logLevel(Log.Level.VERBOSE);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(true));
        }

        @Test
        public void logLevelDebug() throws InterruptedException {
            Log.logLevel(Log.Level.DEBUG);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(1L));
        }

        @Test
        public void logLevelError() throws InterruptedException {
            Log.logLevel(Log.Level.ERROR);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(2L));
        }

        @Test
        public void logLevelSilent() throws InterruptedException {
            Log.logLevel(Log.Level.SILENT);
            sLatch = new CountDownLatch(4);

            callAllLogLevelsOnNewThread();

            assertThat(sLatch.await(100, TimeUnit.MILLISECONDS), is(false));
            assertThat(sLatch.getCount(), is(4L));
        }

        @Test(expected = NullPointerException.class)
        public void nullLogLevel() {
            Log.logLevel(null);
        }

        private void callAllLogLevelsOnNewThread() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.v(TAG, "verbose");
                    Log.d(TAG, "debug");
                    Log.e(TAG, "error");
                    Log.printStackTrace(TAG, new Exception("exception"));
                }
            }).start();
        }

        @Test(expected = NullPointerException.class)
        public void nullWriter() {
            Log.writer(null);
        }
    }

    public static class DefaultWriterTest {
        @BeforeClass
        public static void setupClass() {
            Log.writer(new Log.DefaultWriter());
        }

        @Test
        public void invokeDefaultWriterMethodsVerbose() {
            Log.logLevel(Log.Level.VERBOSE);
            Log.v(TAG, "verbose", "detail");
            Log.v(TAG, "verbose", 1);
            Log.d(TAG, "debug", "detail");
            Log.d(TAG, "debug", 1);
            Log.e(TAG, "error", "detail");
            Log.e(TAG, "error", 1);
            Log.printStackTrace(TAG, new Exception());
        }

        @Test
        public void invokeDefaultWriterMethodsSilent() {
            Log.logLevel(Log.Level.SILENT);
            Log.v(TAG, "verbose", "detail");
            Log.v(TAG, "verbose", 1);
            Log.d(TAG, "debug", "detail");
            Log.d(TAG, "debug", 1);
            Log.e(TAG, "error", "detail");
            Log.e(TAG, "error", 1);
            Log.printStackTrace(TAG, new Exception());
        }
    }
}
