package net.kazyx.wirespider;

class ArgumentCheck {
    private ArgumentCheck() {
    }

    static void rejectNullArgs(Object... args) {
        for (Object arg : args) {
            rejectNull(arg);
        }
    }

    static void rejectNull(Object arg) {
        if (arg == null) {
            throw new NullPointerException("Null argument rejected");
        }
    }
}
