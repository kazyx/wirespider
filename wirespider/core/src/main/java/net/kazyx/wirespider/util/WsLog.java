/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WsLog {
    private WsLog() {
    }

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
         * @param tag Tag of the message.
         * @param message Log message.
         */
        void v(String tag, String message);

        /**
         * Flush normal logs.
         *
         * @param tag Tag of the message.
         * @param message Log message.
         */
        void d(String tag, String message);

        /**
         * Flush error logs.
         *
         * @param tag Tag of the message.
         * @param message Log message.
         */
        void e(String tag, String message);

        /**
         * Flush caught exception logs.
         *
         * @param tag Tag of the message.
         * @param th Caught exception.
         */
        void printStackTrace(String tag, Throwable th);
    }

    public static class DefaultWriter implements Writer {
        private final SimpleDateFormat mDateFormat;

        public DefaultWriter() {
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

    public static void v(String tag, String message) {
        if (sLevel == Level.VERBOSE) {
            sWriter.v(tag, message);
        }
    }

    public static void v(String tag, String message, String detail) {
        if (sLevel == Level.VERBOSE) {
            sWriter.v(tag, message + ": " + detail);
        }
    }

    public static void v(String tag, String message, long detail) {
        if (sLevel == Level.VERBOSE) {
            sWriter.v(tag, message + ": " + detail);
        }
    }

    public static void v(String tag, String message, byte[] detail) {
        if (sLevel == Level.VERBOSE) {
            sWriter.v(tag, message + ": " + BinaryUtil.toHex(detail));
        }
    }

    public static void d(String tag, String message) {
        if (sLevel.level <= Level.DEBUG.level) {
            sWriter.d(tag, message);
        }
    }

    public static void d(String tag, String message, String detail) {
        if (sLevel.level <= Level.DEBUG.level) {
            sWriter.d(tag, message + ": " + detail);
        }
    }

    public static void d(String tag, String message, long detail) {
        if (sLevel.level <= Level.DEBUG.level) {
            sWriter.d(tag, message + ": " + detail);
        }
    }

    public static void e(String tag, String message) {
        if (sLevel.level <= Level.ERROR.level) {
            sWriter.e(tag, message);
        }
    }

    public static void e(String tag, String message, String detail) {
        if (sLevel.level <= Level.ERROR.level) {
            sWriter.e(tag, message + ": " + detail);
        }
    }

    public static void e(String tag, String message, long detail) {
        if (sLevel.level <= Level.ERROR.level) {
            sWriter.e(tag, message + ": " + detail);
        }
    }

    public static void printStackTrace(String tag, Throwable th) {
        if (sLevel.level <= Level.ERROR.level) {
            sWriter.printStackTrace(tag, th);
        }
    }
}
