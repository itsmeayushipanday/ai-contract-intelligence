package com.contractai.backend.exception;

/** The uploaded file exceeds the configured size limit. Mapped to 413. */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
