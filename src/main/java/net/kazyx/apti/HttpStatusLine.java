package net.kazyx.apti;

class HttpStatusLine {
    final String version;
    final int statusCode;
    final String reason;

    HttpStatusLine(String version, int status, String reason) {
        this.version = version;
        this.statusCode = status;
        this.reason = reason;
    }
}
