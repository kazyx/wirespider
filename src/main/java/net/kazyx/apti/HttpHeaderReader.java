package net.kazyx.apti;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class HttpHeaderReader {
    private HttpStatusLine mStatusLine;
    private List<HttpHeader> mHeaders;

    /**
     * @param data HTTP header data as byte array.
     * @throws IOException Failed to parse status line or header fields.
     */
    HttpHeaderReader(byte[] data) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)));
        readStatusLine(reader);
        readHeader(reader);
    }

    /**
     * @return Status line of HTTP response.
     */
    HttpStatusLine getStatusLine() {
        return mStatusLine;
    }

    /**
     * @return Key and values dictionary of HTTP header fields. Keys of header are un-capitalized.
     */
    List<HttpHeader> getHeaderFields() {
        return mHeaders;
    }

    private void readStatusLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("EOF reading status line");
        }

        String[] status = line.split(" ");
        if (status.length < 2) {
            throw new IOException("Failed to read status line: " + line);
        }

        if (!status[0].toLowerCase(Locale.US).startsWith("http/")) {
            throw new IOException("Status line is not HTTP: " + line);
        }

        String version = status[0].substring(5);

        int statusCode;
        try {
            statusCode = Integer.parseInt(status[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Failed to read status statusCode: " + line);
        }

        String reason = line.substring(line.indexOf(status[1]) + status[1].length());

        mStatusLine = new HttpStatusLine(version, statusCode, reason);
    }

    /**
     * Parse HTTP header according to RFC 7230 3.2<br>
     * <li>Header names will be used as key of Map in un-capitalized style</li>
     * <li>The length of value is 1 in most cases. If duplicated header keys exist, it will be 2 or more.</li>
     * <li>Leading and trailing whitespaces of the values are trimmed</li>
     *
     * @param reader HTTP stream reader. Status line must have been read already.
     */
    private void readHeader(BufferedReader reader) throws IOException {
        Map<String, List<String>> headers = new HashMap<>();

        StringBuilder sb = new StringBuilder();
        String name = null;

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("EOF reading header");
            }
            if (line.length() == 0) {
                // End of HTTP header
                break;
            }

            if (name != null) {
                // Check with existence of name, because value might be empty string
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    // This line is continuation of the previous line
                    sb.append("\r\n").append(line);
                    continue;
                }

                // Previous header ends by the previous line
                appendToHeaderContainer(name, sb.toString(), headers);
                name = null;
                sb.setLength(0);
            }

            // Search header token
            if (line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }

            int index = line.indexOf(":");
            if (index <= 0) {
                // No colon, starts with colon
                continue;
            }

            name = line.substring(0, index).toLowerCase(Locale.US);
            sb.append(line.substring(index + 1));
        }

        if (name != null) {
            // last line is valid header
            appendToHeaderContainer(name, sb.toString(), headers);
        }

        List<HttpHeader> parsed = new ArrayList<>(headers.size());
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            parsed.add(new HttpHeader(entry.getKey(), entry.getValue()));
        }
        mHeaders = parsed;
    }

    private static void appendToHeaderContainer(String name, String value, Map<String, List<String>> container) {
        List<String> values = container.get(name);
        if (values == null) {
            values = new ArrayList<>();
            container.put(name, values);
        }
        values.add(value.trim());
    }
}
