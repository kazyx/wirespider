package net.kazyx.apti;

import java.io.Closeable;
import java.io.IOException;

final class IOUtil {
    private IOUtil() {
    }

    static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // Nothing to do
        }
    }
}
