package com.contractai.backend.service.impl;

import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.dto.PageResponse;
import com.contractai.backend.entity.Contract;
import com.contractai.backend.exception.ContractNotFoundException;
import com.contractai.backend.exception.InvalidFileException;
import com.contractai.backend.repository.ContractRepository;
import com.contractai.backend.service.ContractService;
import com.contractai.backend.service.FileStorageService;
import com.contractai.backend.validation.PdfFileValidator;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Contract business rules. Every operation is scoped to the owning user. */
@Slf4j
@Service
public class ContractServiceImpl implements ContractService {

    private static final String DEFAULT_FILENAME = "contract.pdf";
    private static final int MAX_FILENAME_LENGTH = 255;

    private final ContractRepository repository;
    private final FileStorageService storage;
    private final PdfFileValidator validator;

    public ContractServiceImpl(ContractRepository repository, FileStorageService storage, PdfFileValidator validator) {
        this.repository = repository;
        this.storage = storage;
        this.validator = validator;
    }

    // Deliberately NOT @Transactional. A file and a DB row cannot share one transaction. Without an
    // outer transaction, saveAndFlush commits on its own, so every DB failure (including a failed
    // commit) surfaces inside the catch below, where the just-written file is cleaned up.
    @Override
    public ContractResponse upload(UUID ownerId, MultipartFile file) {
        validator.validate(file);

        String storagePath = storeFile(ownerId, file);
        try {
            Contract contract = new Contract();
            contract.setOwnerId(ownerId);
            contract.setOriginalFilename(displayName(file.getOriginalFilename()));
            contract.setContentType("application/pdf"); // verified by content, not taken from the client
            contract.setFileSizeBytes(file.getSize());
            contract.setStoragePath(storagePath);
            // saveAndFlush: createdAt/updatedAt are filled when the INSERT runs (see decisions.md)
            return ContractResponse.from(repository.saveAndFlush(contract));
        } catch (RuntimeException e) {
            removeOrphan(storagePath, e);
            throw e;
        }
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

    private String storeFile(UUID ownerId, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return storage.store(ownerId, in);
        } catch (IOException e) {
            throw new InvalidFileException("The uploaded file could not be read.");
        }
    }

    /** Best effort. If cleanup itself fails we log it, and the original error is still the one reported. */
    private void removeOrphan(String storagePath, RuntimeException original) {
        try {
            storage.delete(storagePath);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Could not remove orphaned file {}", storagePath, cleanupFailure);
            original.addSuppressed(cleanupFailure);
        }
    }

    /** The name is for display only (never used on disk): strip path parts and control characters, cap length. */
    private static String displayName(String raw) {
        String name = raw == null ? "" : raw;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return DEFAULT_FILENAME;
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(0, MAX_FILENAME_LENGTH) : name;
    }

    private Contract findOwned(UUID ownerId, UUID id) {
        return repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ContractNotFoundException(id));
    }
}
