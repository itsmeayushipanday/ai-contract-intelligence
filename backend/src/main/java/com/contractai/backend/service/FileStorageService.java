package com.contractai.backend.service;

import java.io.InputStream;
import java.util.UUID;

/**
 * Where contract files live. The rest of the app only knows this interface, so the local-disk
 * implementation can later be replaced by S3 without touching controllers or business logic.
 */
public interface FileStorageService {

    /** Saves the PDF bytes under a server-generated name. Returns the path to keep in the database. */
    String store(UUID ownerId, InputStream content);

    /** Opens a stored file. The caller must close the stream. */
    InputStream load(String storagePath);

    /** Deletes a stored file. Deleting a file that is already gone is not an error. */
    void delete(String storagePath);
}
