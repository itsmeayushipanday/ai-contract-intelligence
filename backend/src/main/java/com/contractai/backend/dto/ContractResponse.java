package com.contractai.backend.dto;

import com.contractai.backend.entity.Contract;
import com.contractai.backend.enums.ContractStatus;
import java.time.Instant;
import java.util.UUID;

/** What the API returns. Deliberately omits ownerId and storagePath (internal details). */
public record ContractResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        ContractStatus status,
        Integer pageCount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static ContractResponse from(Contract c) {
        return new ContractResponse(
                c.getId(),
                c.getOriginalFilename(),
                c.getContentType(),
                c.getFileSizeBytes(),
                c.getStatus(),
                c.getPageCount(),
                c.getFailureReason(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
