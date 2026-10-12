package com.contractai.backend.service.impl;

import com.contractai.backend.config.StorageProperties;
import com.contractai.backend.exception.StorageException;
import com.contractai.backend.service.FileStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Stores files on local disk as {root}/{ownerId}/{random-uuid}.pdf. */
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
        // absolute + normalized, so the "stays inside root" check below is reliable
        this.root = Path.of(properties.location()).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID ownerId, InputStream content) {
        // The name is generated here. The client's filename is never used for the disk path.
        String relativePath = ownerId + "/" + UUID.randomUUID() + ".pdf";
        Path target = resolveInsideRoot(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
        } catch (IOException e) {
            deleteQuietly(target); // do not leave a half-written file behind
            throw new StorageException("Could not store file", e);
        }
        return relativePath;
    }

    @Override
    public InputStream load(String storagePath) {
        try {
            return Files.newInputStream(resolveInsideRoot(storagePath));
        } catch (NoSuchFileException e) {
            throw new StorageException("Stored file not found", e);
        } catch (IOException e) {
            throw new StorageException("Could not read file", e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolveInsideRoot(storagePath));
        } catch (IOException e) {
            throw new StorageException("Could not delete file", e);
        }
    }

    /** Defense against path traversal: the final path must still be inside the storage root. */
    private Path resolveInsideRoot(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Invalid storage path");
        }
        return resolved;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort cleanup; the original failure is what gets reported
        }
    }
}
