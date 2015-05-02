package net.kazyx.apti;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP header structure.
 */
public class HttpHeader {
    public static final String COOKIE = "Cookie";
    public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";

    final String key;

    final List<String> values = new ArrayList<>();

    /**
     * Create an HTTP header with single value.
     *
     * @param key   Header key.
     * @param value Header value.
     */
    public HttpHeader(String key, String value) {
        this.key = key;
        this.values.add(value);
    }

    /**
     * Create an HTTP header with multiple values.
     *
     * @param key    Header key
     * @param values List of header values.
     */
    public HttpHeader(String key, List<String> values) {
        this.key = key;
        this.values.addAll(values);
    }

    /**
     * Append an additional header value to this header.
     *
     * @param value Additional header value.
     */
    public void append(String value) {
        values.add(value);
    }

    /**
     * Convert to HTTP header line.<br>
     * Multiple header values are separated by {@code ";"} except that Cookie header is separated by {@code ","}.
     *
     * @return Single line expression of HTTP header.
     */
    public String toHeaderLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(key).append(": ");
        char separator = key.equals(COOKIE) ? ';' : ',';

        boolean first = true;
        for (String value : values) {
            if (!first) {
                sb.append(separator).append(value);
            } else {
                sb.append(value);
                first = false;
            }
        }

        return sb.toString();
    }
}
