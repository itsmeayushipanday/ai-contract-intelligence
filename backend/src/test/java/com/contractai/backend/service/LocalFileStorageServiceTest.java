package com.contractai.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contractai.backend.config.StorageProperties;
import com.contractai.backend.exception.StorageException;
import com.contractai.backend.exception.StoredFileNotFoundException;
import com.contractai.backend.service.impl.LocalFileStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Plain unit test: uses a throwaway temp folder, so no Spring and no real uploads folder.
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storage;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorageService(new StorageProperties(tempDir.toString()));
    }

    private ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storeWritesFileUnderOwnerFolderWithGeneratedName() throws IOException {
        String path = storage.store(owner, bytes("%PDF-1.7 hello"));

        assertThat(path).startsWith(owner + "/").endsWith(".pdf");
        assertThat(Files.readString(tempDir.resolve(path))).isEqualTo("%PDF-1.7 hello");
    }

    @Test
    void twoStoresNeverShareAName() {
        assertThat(storage.store(owner, bytes("a"))).isNotEqualTo(storage.store(owner, bytes("a")));
    }

    @Test
    void loadReturnsStoredBytes() throws IOException {
        String path = storage.store(owner, bytes("contract text"));

        try (var in = storage.load(path)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("contract text");
        }
    }

    @Test
    void loadOfMissingFileThrowsStorageException() {
        assertThatThrownBy(() -> storage.load(owner + "/missing.pdf"))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void deleteRemovesFileAndIsHarmlessWhenAlreadyGone() {
        String path = storage.store(owner, bytes("x"));

        storage.delete(path);

        assertThat(Files.exists(tempDir.resolve(path))).isFalse();
        assertThatCode(() -> storage.delete(path)).doesNotThrowAnyException();
    }

    @Test
    void pathTraversalIsRejectedForLoadAndDelete() throws IOException {
        Path outside = Files.createTempFile("outside-", ".pdf");
        try {
            String escape = tempDir.relativize(outside).toString().replace('\\', '/');
            assertThatThrownBy(() -> storage.load("../" + escape)).isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> storage.delete("../../etc/passwd")).isInstanceOf(StorageException.class);
            assertThat(Files.exists(outside)).isTrue();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void absolutePathIsRejected() {
        String absolute = tempDir.getRoot().resolve("Windows").toString();
        assertThatThrownBy(() -> storage.delete(absolute)).isInstanceOf(StorageException.class);
    }
}
