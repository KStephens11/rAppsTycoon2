package com.rapptycoon.exception;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String code) {
        super("Session not found: " + code);
    }
}
