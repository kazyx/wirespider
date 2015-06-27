package net.kazyx.wirespider.http;

import net.kazyx.wirespider.util.ArgumentCheck;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP header structure.
 */
public class HttpHeader {
    public static final String COOKIE = "Cookie";
    public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    public static final String UPGRADE = "Upgrade";
    public static final String CONNECTION = "Connection";
    public static final String SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";
    public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    final String key;

    public String key() {
        return key;
    }

    private final List<String> values = new ArrayList<>();

    public List<String> values() {
        return values;
    }

    public static class Builder {
        private final String name;
        private final List<String> values = new ArrayList<>();

        public Builder(String name) {
            ArgumentCheck.rejectNull(name);
            this.name = name;
        }

        public Builder appendValue(String value) {
            ArgumentCheck.rejectNull(value);
            values.add(value);
            return this;
        }

        public HttpHeader build() {
            return new HttpHeader(name, values);
        }
    }

    /**
     * Create an HTTP header with multiple values.
     *
     * @param key Header key
     * @param values List of header values.
     */
    private HttpHeader(String key, List<String> values) {
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
        char separator = key.equalsIgnoreCase(COOKIE) ? ';' : ',';

        boolean first = true;
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (!first) {
                sb.append(separator).append(value);
            } else {
                sb.append(value);
                first = false;
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toHeaderLine();
    }
}
