package com.contractai.backend.service.impl;

import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.dto.CreateContractRequest;
import com.contractai.backend.dto.PageResponse;
import com.contractai.backend.entity.Contract;
import com.contractai.backend.exception.ContractNotFoundException;
import com.contractai.backend.repository.ContractRepository;
import com.contractai.backend.service.ContractService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contract business rules. Every operation is scoped to the owning user. */
@Service
public class ContractServiceImpl implements ContractService {

    private final ContractRepository repository;

    public ContractServiceImpl(ContractRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ContractResponse create(UUID ownerId, CreateContractRequest request) {
        Contract contract = new Contract();
        contract.setOwnerId(ownerId);
        contract.setOriginalFilename(request.originalFilename());
        contract.setContentType(request.contentType());
        contract.setFileSizeBytes(request.fileSizeBytes());
        // saveAndFlush, not save: Hibernate fills createdAt/updatedAt when the INSERT runs, which
        // would otherwise be delayed until commit, after the response has already been built.
        return ContractResponse.from(repository.saveAndFlush(contract));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> list(UUID ownerId, Pageable pageable) {
        return PageResponse.from(repository.findByOwnerId(ownerId, pageable), ContractResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ContractResponse get(UUID ownerId, UUID id) {
        return ContractResponse.from(findOwned(ownerId, id));
    }

    // Later phases will also remove the stored file, chunks and embeddings in this same operation.
    @Override
    @Transactional
    public void delete(UUID ownerId, UUID id) {
        repository.delete(findOwned(ownerId, id));
    }

    private Contract findOwned(UUID ownerId, UUID id) {
        return repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ContractNotFoundException(id));
    }
}
