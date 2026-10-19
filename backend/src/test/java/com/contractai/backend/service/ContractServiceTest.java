package com.contractai.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.contractai.backend.dto.ContractDownload;
import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.entity.Contract;
import com.contractai.backend.enums.ContractStatus;
import com.contractai.backend.exception.ContractNotFoundException;
import com.contractai.backend.exception.FileTooLargeException;
import com.contractai.backend.exception.InvalidFileException;
import com.contractai.backend.exception.StorageException;
import com.contractai.backend.exception.StoredFileNotFoundException;
import com.contractai.backend.repository.ContractRepository;
import com.contractai.backend.service.impl.ContractServiceImpl;
import com.contractai.backend.validation.PdfFileValidator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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

    private Contract contractWithFile() {
        Contract contract = new Contract();
        contract.setOriginalFilename("vendor.pdf");
        contract.setFileSizeBytes(13);
        contract.setStoragePath(STORED_PATH);
        return contract;
    }

    @Test
    void downloadReturnsStoredFileForOwner() throws Exception {
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.of(contractWithFile()));
        when(storage.load(STORED_PATH)).thenReturn(new ByteArrayInputStream("%PDF-1.7 body".getBytes()));

        ContractDownload download = service.download(owner, contractId);

        assertThat(download.filename()).isEqualTo("vendor.pdf");
        assertThat(download.sizeBytes()).isEqualTo(13);
        assertThat(new String(download.content().readAllBytes())).isEqualTo("%PDF-1.7 body");
    }

    @Test
    void downloadByAnotherUserIsNotFoundAndNeverTouchesStorage() {
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(owner, contractId)).isInstanceOf(ContractNotFoundException.class);

        verifyNoInteractions(storage);
    }

    @Test
    void downloadOfOldRowWithoutStoredFileThrowsStoredFileNotFound() {
        Contract legacy = new Contract(); // storagePath is null, like rows created before uploads existed
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.download(owner, contractId))
                .isInstanceOf(StoredFileNotFoundException.class);

        verifyNoInteractions(storage);
    }

    @Test
    void deleteRemovesRowThenFile() {
        Contract contract = contractWithFile();
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.of(contract));

        service.delete(owner, contractId);

        InOrder order = inOrder(repository, storage);
        order.verify(repository).delete(contract); // the row goes first
        order.verify(storage).delete(STORED_PATH); // then the file
    }

    @Test
    void deleteStillSucceedsWhenTheFileCannotBeRemoved() {
        Contract contract = contractWithFile();
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.of(contract));
        doThrow(new StorageException("disk locked")).when(storage).delete(STORED_PATH);

        service.delete(owner, contractId); // must not throw: the contract is already deleted

        verify(repository).delete(contract);
    }

    @Test
    void deleteOfOldRowWithoutStoredFileSkipsStorage() {
        Contract legacy = new Contract();
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.of(legacy));

        service.delete(owner, contractId);

        verify(repository).delete(legacy);
        verifyNoInteractions(storage);
    }

    @Test
    void deleteDoesNotTouchAnythingWhenNotOwned() {
        when(repository.findByIdAndOwnerId(contractId, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(owner, contractId))
                .isInstanceOf(ContractNotFoundException.class);
        verify(repository, never()).delete(any(Contract.class));
        verifyNoInteractions(storage); // someone else's file must stay untouched
    }
}
