package com.contractai.backend.exception;

import java.util.UUID;

/**
 * Thrown when a contract does not exist OR belongs to someone else (same message on purpose,
 * so callers cannot probe which IDs exist). Mapped to 404 by GlobalExceptionHandler.
 */
public class ContractNotFoundException extends RuntimeException {

    public ContractNotFoundException(UUID id) {
        super("Contract not found: " + id);
    }
}
