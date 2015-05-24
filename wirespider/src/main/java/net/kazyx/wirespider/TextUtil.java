package net.kazyx.wirespider;

class TextUtil {
    private TextUtil() {
    }

    /**
     * @param text Source text.
     * @return {@code true} if the text is {@code null} or 0-length.
     */
    static boolean isNullOrEmpty(String text) {
        return text == null || text.isEmpty();
    }
}
