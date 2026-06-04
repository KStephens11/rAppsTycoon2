package com.rapptycoon.exception;

public class SessionFullException extends RuntimeException {

    public SessionFullException(String code) {
        super("Session is full: " + code);
    }
}
