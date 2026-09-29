package com.personalkanban.infrastructure.sqlite;

/** Wraps low-level persistence failures so callers see one clear exception type. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
