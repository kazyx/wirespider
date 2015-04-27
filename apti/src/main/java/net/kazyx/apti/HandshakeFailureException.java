package net.kazyx.apti;

class HandshakeFailureException extends Exception {
    HandshakeFailureException(String message) {
        super(message);
    }

    HandshakeFailureException(Throwable th) {
        super(th);
    }
}
