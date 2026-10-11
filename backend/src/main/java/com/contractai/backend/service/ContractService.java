package com.contractai.backend.service;

import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.dto.CreateContractRequest;
import com.contractai.backend.dto.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/** Business operations for contracts. Every method is scoped to the owning user. */
public interface ContractService {

    ContractResponse create(UUID ownerId, CreateContractRequest request);

    PageResponse<ContractResponse> list(UUID ownerId, Pageable pageable);

    ContractResponse get(UUID ownerId, UUID id);

    void delete(UUID ownerId, UUID id);
}
