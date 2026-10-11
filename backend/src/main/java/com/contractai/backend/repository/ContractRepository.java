package com.contractai.backend.repository;

import com.contractai.backend.entity.Contract;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring generates the implementation at startup. Method names are parsed into SQL,
 * e.g. findByIdAndOwnerId -> WHERE id = ? AND owner_id = ?
 */
public interface ContractRepository extends JpaRepository<Contract, UUID> {

    Page<Contract> findByOwnerId(UUID ownerId, Pageable pageable);

    /** Always look contracts up with the owner, so one user can never read another's. */
    Optional<Contract> findByIdAndOwnerId(UUID id, UUID ownerId);
}
