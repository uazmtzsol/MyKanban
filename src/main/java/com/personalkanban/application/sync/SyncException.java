package com.personalkanban.application.sync;

/**
 * A sync operation could not be completed: transport failure, bad
 * credentials, a rejected payload, or a server-side error.
 * {@link #kind()} lets the UI turn each case into the right message
 * without parsing strings; {@code statusCode} is the HTTP status
 * when there was one (0 for pure transport failures).
 */
public final class SyncException extends RuntimeException {

    public enum Kind { NETWORK, UNAUTHORIZED, BAD_REQUEST, TOO_LARGE, SERVER }

    private final Kind kind;
    private final int statusCode;

    public SyncException(Kind kind, int statusCode, String message) {
        super(message);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public SyncException(Kind kind, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.statusCode = statusCode;
    }

    public Kind kind() {
        return kind;
    }

    public int statusCode() {
        return statusCode;
    }
}
