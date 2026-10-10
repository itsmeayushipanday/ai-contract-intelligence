package com.contractai.backend.enums;

/** Where a contract is in the processing pipeline. Names must match the CHECK constraint in V2. */
public enum ContractStatus {
    UPLOADED,
    PROCESSING,
    COMPLETED,
    FAILED
}
