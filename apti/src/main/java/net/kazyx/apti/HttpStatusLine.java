package net.kazyx.apti;

class HttpStatusLine {
    private final String mVersion;
    private final int mStatusCode;
    private final String mReason;

    HttpStatusLine(String version, int status, String reason) {
        this.mVersion = version;
        this.mStatusCode = status;
        this.mReason = reason;
    }

    String version() {
        return mVersion;
    }

    int statusCode() {
        return mStatusCode;
    }

    String reason() {
        return mReason;
    }
}
