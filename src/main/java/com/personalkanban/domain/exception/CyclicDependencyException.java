package com.personalkanban.domain.exception;

/** Thrown when a card link would create a cycle in the precedence graph. */
public class CyclicDependencyException extends DomainException {

    public CyclicDependencyException(String message) {
        super(message);
    }
}
