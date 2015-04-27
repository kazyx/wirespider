package net.kazyx.apti;

import java.util.ArrayList;
import java.util.List;

public class HttpHeader {
    public static final String COOKIE = "Cookie";
    public static final String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";

    final String key;

    final List<String> values = new ArrayList<>();

    public HttpHeader(String key, String value) {
        this.key = key;
        this.values.add(value);
    }

    public HttpHeader(String key, List<String> values) {
        this.key = key;
        this.values.addAll(values);
    }

    public void append(String value) {
        values.add(value);
    }

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
