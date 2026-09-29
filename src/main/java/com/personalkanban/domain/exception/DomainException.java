package com.personalkanban.domain.exception;

/** Base type for all rule violations raised by the domain layer. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
