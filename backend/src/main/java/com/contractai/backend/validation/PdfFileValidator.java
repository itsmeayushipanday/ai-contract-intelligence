package com.contractai.backend.validation;

import com.contractai.backend.config.UploadProperties;
import com.contractai.backend.exception.FileTooLargeException;
import com.contractai.backend.exception.InvalidFileException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Decides whether an upload is acceptable. The file's real content is the authority:
 * the client-supplied filename and Content-Type are not trusted (both are trivially faked).
 */
@Component
public class PdfFileValidator {

    // Every PDF starts with these 5 bytes.
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final UploadProperties properties;

    public PdfFileValidator(UploadProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("The uploaded file is empty.");
        }
        long maxBytes = properties.maxFileSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new FileTooLargeException("The file exceeds the maximum size of " + properties.maxFileSize() + ".");
        }
        if (!startsWithPdfMagic(file)) {
            throw new InvalidFileException("The file is not a valid PDF.");
        }
    }

    private boolean startsWithPdfMagic(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            // a file shorter than 5 bytes yields a shorter array, which simply won't match
            return Arrays.equals(in.readNBytes(PDF_MAGIC.length), PDF_MAGIC);
        } catch (IOException e) {
            throw new InvalidFileException("The uploaded file could not be read.");
        }
    }
}
