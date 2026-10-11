package com.contractai.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.dto.CreateContractRequest;
import com.contractai.backend.entity.Contract;
import com.contractai.backend.enums.ContractStatus;
import com.contractai.backend.exception.ContractNotFoundException;
import com.contractai.backend.repository.ContractRepository;
import com.contractai.backend.service.impl.ContractServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Pure unit test: the repository is a Mockito fake, so no database or Spring is involved.
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository repository;

    @InjectMocks
    private ContractServiceImpl service;

    private final UUID owner = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();

    @Test
    void createSavesContractOwnedByCallerWithUploadedStatus() {
        when(repository.saveAndFlush(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractResponse response =
                service.create(owner, new CreateContractRequest("vendor.pdf", "application/pdf", 2048));

        ArgumentCaptor<Contract> saved = ArgumentCaptor.forClass(Contract.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(owner);
        assertThat(saved.getValue().getOriginalFilename()).isEqualTo("vendor.pdf");
        assertThat(response.status()).isEqualTo(ContractStatus.UPLOADED);
        assertThat(response.fileSizeBytes()).isEqualTo(2048);
    }

    @Test
    void getReturnsContractWhenOwnedByCaller() {
        Contract contract = new Contract();
        contract.setOriginalFilename("nda.pdf");
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.of(contract));

        assertThat(service.get(owner, contractId).originalFilename()).isEqualTo("nda.pdf");
    }

    @Test
    void getThrowsNotFoundWhenMissingOrOwnedBySomeoneElse() {
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(owner, contractId))
                .isInstanceOf(ContractNotFoundException.class);
    }

    @Test
    void deleteRemovesOwnedContract() {
        Contract contract = new Contract();
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.of(contract));

        service.delete(owner, contractId);

        verify(repository).delete(contract);
    }

    @Test
    void deleteDoesNotTouchAnythingWhenNotOwned() {
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(owner, contractId))
                .isInstanceOf(ContractNotFoundException.class);
        verify(repository, never()).delete(any(Contract.class));
    }
}
