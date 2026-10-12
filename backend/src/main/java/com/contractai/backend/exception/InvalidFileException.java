package com.contractai.backend.exception;

/** The uploaded file is empty, unreadable or not a PDF. Mapped to 400. */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }
}
