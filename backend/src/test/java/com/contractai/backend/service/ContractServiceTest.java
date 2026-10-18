package com.contractai.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.entity.Contract;
import com.contractai.backend.enums.ContractStatus;
import com.contractai.backend.exception.ContractNotFoundException;
import com.contractai.backend.exception.FileTooLargeException;
import com.contractai.backend.exception.InvalidFileException;
import com.contractai.backend.exception.StorageException;
import com.contractai.backend.repository.ContractRepository;
import com.contractai.backend.service.impl.ContractServiceImpl;
import com.contractai.backend.validation.PdfFileValidator;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

// Pure unit test: repository, storage and validator are Mockito fakes, so no database or Spring.
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    private static final String STORED_PATH = "owner/stored-name.pdf";

    @Mock
    private ContractRepository repository;

    @Mock
    private FileStorageService storage;

    @Mock
    private PdfFileValidator validator;

    @InjectMocks
    private ContractServiceImpl service;

    private final UUID owner = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", "%PDF-1.7 body".getBytes());
    }

    private Contract savedContract() {
        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(repository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void uploadStoresFileThenSavesContractOwnedByCaller() {
        when(storage.store(eq(owner), any(InputStream.class))).thenReturn(STORED_PATH);
        when(repository.saveAndFlush(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = pdf("vendor.pdf");

        ContractResponse response = service.upload(owner, file);

        Contract saved = savedContract();
        assertThat(saved.getOwnerId()).isEqualTo(owner);
        assertThat(saved.getStoragePath()).isEqualTo(STORED_PATH);
        assertThat(saved.getOriginalFilename()).isEqualTo("vendor.pdf");
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getFileSizeBytes()).isEqualTo(file.getSize());
        assertThat(response.status()).isEqualTo(ContractStatus.UPLOADED);
        verify(storage, never()).delete(any());
    }

    @Test
    void uploadIgnoresClientContentTypeAndRecordsPdf() {
        when(storage.store(eq(owner), any(InputStream.class))).thenReturn(STORED_PATH);
        when(repository.saveAndFlush(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile liar = new MockMultipartFile("file", "x.pdf", "text/html", "%PDF-1.7".getBytes());

        service.upload(owner, liar);

        assertThat(savedContract().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void rejectedFileIsNeitherStoredNorSaved() {
        MockMultipartFile bad = pdf("bad.pdf");
        doThrow(new InvalidFileException("not a pdf")).when(validator).validate(bad);

        assertThatThrownBy(() -> service.upload(owner, bad)).isInstanceOf(InvalidFileException.class);

        verifyNoInteractions(storage, repository);
    }

    @Test
    void oversizedFileIsNeitherStoredNorSaved() {
        MockMultipartFile big = pdf("big.pdf");
        doThrow(new FileTooLargeException("too big")).when(validator).validate(big);

        assertThatThrownBy(() -> service.upload(owner, big)).isInstanceOf(FileTooLargeException.class);

        verifyNoInteractions(storage, repository);
    }

    @Test
    void databaseFailureDeletesTheJustWrittenFileAndRethrows() {
        when(storage.store(eq(owner), any(InputStream.class))).thenReturn(STORED_PATH);
        when(repository.saveAndFlush(any(Contract.class))).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.upload(owner, pdf("a.pdf")))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(storage).delete(STORED_PATH);
    }

    @Test
    void failedCleanupDoesNotHideTheOriginalError() {
        when(storage.store(eq(owner), any(InputStream.class))).thenReturn(STORED_PATH);
        when(repository.saveAndFlush(any(Contract.class))).thenThrow(new DataIntegrityViolationException("boom"));
        doThrow(new StorageException("disk gone")).when(storage).delete(STORED_PATH);

        assertThatThrownBy(() -> service.upload(owner, pdf("a.pdf")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasSuppressedException(new StorageException("disk gone"));
    }

    @Test
    void storageFailureMeansNothingIsSaved() {
        when(storage.store(eq(owner), any(InputStream.class))).thenThrow(new StorageException("disk full"));

        assertThatThrownBy(() -> service.upload(owner, pdf("a.pdf"))).isInstanceOf(StorageException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void filenameIsSanitizedForDisplay() {
        when(storage.store(eq(owner), any(InputStream.class))).thenReturn(STORED_PATH);
        when(repository.saveAndFlush(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        String[][] cases = {
                {"C:\\Users\\bob\\vendor.pdf", "vendor.pdf"},
                {"../../evil.pdf", "evil.pdf"},
                {"", "contract.pdf"},
                {"a".repeat(300), "a".repeat(255)},
        };
        for (String[] c : cases) {
            ContractResponse response = service.upload(owner, pdf(c[0]));
            assertThat(response.originalFilename()).as("input: %s", c[0]).isEqualTo(c[1]);
        }
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
