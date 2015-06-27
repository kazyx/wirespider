package net.kazyx.wirespider.http;

public class HttpStatusLine {
    private final String mVersion;
    private final int mStatusCode;
    private final String mReason;

    HttpStatusLine(String version, int status, String reason) {
        this.mVersion = version;
        this.mStatusCode = status;
        this.mReason = reason;
    }

    public String version() {
        return mVersion;
    }

    public int statusCode() {
        return mStatusCode;
    }

    public String reason() {
        return mReason;
    }
}
