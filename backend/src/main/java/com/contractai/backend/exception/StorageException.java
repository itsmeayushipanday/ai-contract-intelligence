package com.contractai.backend.exception;

/** A file could not be stored, read or deleted. Mapped to a generic 500 (details stay in the log). */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
