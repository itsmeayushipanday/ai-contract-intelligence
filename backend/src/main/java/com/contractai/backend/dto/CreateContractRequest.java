package com.contractai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of POST /api/contracts. The annotations are enforced because the controller uses @Valid. */
public record CreateContractRequest(
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 100) String contentType,
        @Positive long fileSizeBytes) {
}
