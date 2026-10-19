package com.contractai.backend.service;

import com.contractai.backend.dto.ContractDownload;
import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.dto.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/** Business operations for contracts. Every method is scoped to the owning user. */
public interface ContractService {

    /** Validates and stores the PDF, then records it. Nothing is kept if any step fails. */
    ContractResponse upload(UUID ownerId, MultipartFile file);

    PageResponse<ContractResponse> list(UUID ownerId, Pageable pageable);

    ContractResponse get(UUID ownerId, UUID id);

    /** Opens the stored PDF. 404 if the contract is not the caller's or has no stored file. */
    ContractDownload download(UUID ownerId, UUID id);

    /** Removes the contract record and its stored file. */
    void delete(UUID ownerId, UUID id);
}
