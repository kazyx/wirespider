package net.kazyx.apti;

public class Logger {
    static void d(String tag, String message) {
        System.out.println(tag + ": " + message);
    }

    static void stacktrace(String tag, Throwable th) {
        th.printStackTrace();
    }
}
