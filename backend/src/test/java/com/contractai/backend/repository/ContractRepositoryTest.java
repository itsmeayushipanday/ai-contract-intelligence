package com.contractai.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contractai.backend.entity.Contract;
import com.contractai.backend.enums.ContractStatus;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

// NONE = use the real local PostgreSQL (so Flyway migrations and CHECK constraints are tested),
// not an in-memory substitute. Each test rolls back, so no data is left behind.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContractRepositoryTest {

    @Autowired
    private ContractRepository repository;

    @Autowired
    private EntityManager entityManager;

    private Contract newContract(UUID ownerId, String filename) {
        Contract c = new Contract();
        c.setOwnerId(ownerId);
        c.setOriginalFilename(filename);
        c.setContentType("application/pdf");
        c.setFileSizeBytes(1024);
        return c;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear(); // forces the next read to hit the database, not the cache
    }

    @Test
    void savePersistsContractWithGeneratedIdDefaultStatusAndTimestamps() {
        Contract saved = repository.save(newContract(UUID.randomUUID(), "vendor.pdf"));
        flushAndClear();

        Contract found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getStatus()).isEqualTo(ContractStatus.UPLOADED);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getOriginalFilename()).isEqualTo("vendor.pdf");
    }

    @Test
    void findByIdAndOwnerIdReturnsEmptyForDifferentOwner() {
        UUID owner = UUID.randomUUID();
        Contract saved = repository.save(newContract(owner, "nda.pdf"));
        flushAndClear();

        assertThat(repository.findByIdAndOwnerId(saved.getId(), owner)).isPresent();
        assertThat(repository.findByIdAndOwnerId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByOwnerIdReturnsOnlyThatOwnersContractsPaged() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        repository.save(newContract(alice, "a1.pdf"));
        repository.save(newContract(alice, "a2.pdf"));
        repository.save(newContract(alice, "a3.pdf"));
        repository.save(newContract(bob, "b1.pdf"));
        flushAndClear();

        var firstPage = repository.findByOwnerId(alice, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent()).allMatch(c -> c.getOwnerId().equals(alice));
    }

    @Test
    void databaseRejectsInvalidStatusEvenIfCodeBypassesTheEnum() {
        Contract saved = repository.save(newContract(UUID.randomUUID(), "bad.pdf"));
        flushAndClear();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("UPDATE contracts SET status = 'BOGUS' WHERE id = :id")
                    .setParameter("id", saved.getId())
                    .executeUpdate();
        }).hasMessageContaining("contracts_status_check");
    }
}
