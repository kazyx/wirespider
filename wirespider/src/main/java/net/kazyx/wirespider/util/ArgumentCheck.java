package net.kazyx.wirespider.util;

public class ArgumentCheck {
    private ArgumentCheck() {
    }

    public static void rejectNullArgs(Object... args) {
        for (Object arg : args) {
            rejectNull(arg);
        }
    }

    public static void rejectNull(Object arg) {
        if (arg == null) {
            throw new NullPointerException("Null argument rejected");
        }
    }
}
