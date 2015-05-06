package net.kazyx.apti;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AptiLog {
    public enum Level {
        VERBOSE(0),
        DEBUG(10),
        ERROR(20),
        SILENT(30);

        private final int level;

        Level(int num) {
            level = num;
        }
    }

    public interface Writer {
        /**
         * Flush verbose logs.
         *
         * @param tag     Tag of the message.
         * @param message Log message.
         */
        void v(String tag, String message);

        /**
         * Flush normal logs.
         *
         * @param tag     Tag of the message.
         * @param message Log message.
         */
        void d(String tag, String message);

        /**
         * Flush error logs.
         *
         * @param tag     Tag of the message.
         * @param message Log message.
         */
        void e(String tag, String message);

        /**
         * Flush caught exception logs.
         *
         * @param tag Tag of the message.
         * @param th  Caught exception.
         */
        void printStackTrace(String tag, Throwable th);
    }

    private static class DefaultWriter implements Writer {
        private final SimpleDateFormat mDateFormat;

        private DefaultWriter() {
            mDateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        }

        @Override
        public void v(String tag, String message) {
            System.out.println(mDateFormat.format(new Date()) + ":V: " + tag + ": " + message);
        }

        @Override
        public void d(String tag, String message) {
            System.out.println(mDateFormat.format(new Date()) + ":D: " + tag + ": " + message);
        }

        @Override
        public void e(String tag, String message) {
            System.err.println(mDateFormat.format(new Date()) + ":E: " + tag + ": " + message);
        }

        @Override
        public void printStackTrace(String tag, Throwable th) {
            System.out.println(mDateFormat.format(new Date()) + ":X: " + tag + ":");
            th.printStackTrace();
        }
    }

    /**
     * Set log writer instance.
     *
     * @param writer Writer.
     */
    public static void writer(Writer writer) {
        ArgumentCheck.rejectNull(writer);
        sWriter = writer;
    }

    private static Level sLevel = Level.DEBUG;

    /**
     * Set level of the logs to be flushed.<br>
     * Log level is {@link Level#DEBUG} by default.
     *
     * @param level Level.
     */
    public static void logLevel(Level level) {
        ArgumentCheck.rejectNull(level);
        sLevel = level;
    }

    private static Writer sWriter = new DefaultWriter();

    static void v(String tag, String message) {
        if (sLevel == Level.VERBOSE) {
            sWriter.v(tag, message);
        }
    }

    static void v(String tag, String message, String detail) {
        if (sLevel == Level.VERBOSE) {
            sWriter.v(tag, message + ": " + detail);
        }
    }

    static void v(String tag, String message, long detail) {
        if (sLevel == Level.VERBOSE) {
            sWriter.v(tag, message + ": " + detail);
        }
    }

    static void d(String tag, String message) {
        if (sLevel.level <= Level.DEBUG.level) {
            sWriter.d(tag, message);
        }
    }

    static void d(String tag, String message, String detail) {
        if (sLevel.level <= Level.DEBUG.level) {
            sWriter.d(tag, message + ": " + detail);
        }
    }

    static void d(String tag, String message, long detail) {
        if (sLevel.level <= Level.DEBUG.level) {
            sWriter.d(tag, message + ": " + detail);
        }
    }

    static void e(String tag, String message) {
        if (sLevel.level <= Level.ERROR.level) {
            sWriter.e(tag, message);
        }
    }

    static void e(String tag, String message, String detail) {
        if (sLevel.level <= Level.ERROR.level) {
            sWriter.e(tag, message + ": " + detail);
        }
    }

    static void e(String tag, String message, long detail) {
        if (sLevel.level <= Level.ERROR.level) {
            sWriter.e(tag, message + ": " + detail);
        }
    }

    static void printStackTrace(String tag, Throwable th) {
        if (sLevel.level <= Level.ERROR.level) {
            sWriter.printStackTrace(tag, th);
        }
    }
}
