package net.kazyx.wirespider;

import java.io.Closeable;
import java.io.IOException;

final class IOUtil {
    private IOUtil() {
    }

    static void close(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            // Nothing to do
        }
    }
}
