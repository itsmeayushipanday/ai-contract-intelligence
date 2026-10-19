package com.contractai.backend.exception;

/**
 * The contract exists but its file is not available: either no file was ever recorded for it (old
 * metadata-only rows) or the file is missing from storage. Mapped to 404 by GlobalExceptionHandler.
 */
public class StoredFileNotFoundException extends StorageException {

    public StoredFileNotFoundException(String message) {
        super(message);
    }

    public StoredFileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
