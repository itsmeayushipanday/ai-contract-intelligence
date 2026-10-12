package com.contractai.backend.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contractai.backend.config.UploadProperties;
import com.contractai.backend.exception.FileTooLargeException;
import com.contractai.backend.exception.InvalidFileException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class PdfFileValidatorTest {

    // tiny limit so the "too large" case needs only a few bytes
    private final PdfFileValidator validator =
            new PdfFileValidator(new UploadProperties(DataSize.ofBytes(100)));

    private MockMultipartFile file(String name, String contentType, String content) {
        return new MockMultipartFile("file", name, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void acceptsRealPdfBytes() {
        assertThatCode(() -> validator.validate(file("vendor.pdf", "application/pdf", "%PDF-1.7 body")))
                .doesNotThrowAnyException();
    }

    @Test
    void contentDecidesNotTheNameOrDeclaredType() {
        // genuine PDF bytes are accepted even with an odd name/type...
        assertThatCode(() -> validator.validate(file("noext", "application/octet-stream", "%PDF-1.4")))
                .doesNotThrowAnyException();
        // ...and a fake "pdf" that is not a PDF is rejected despite name and type claiming otherwise
        assertThatThrownBy(() -> validator.validate(file("fake.pdf", "application/pdf", "MZ this is an exe")))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validate(file("empty.pdf", "application/pdf", "")))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidFileException.class);
    }

    @Test
    void rejectsFileShorterThanTheMagicHeader() {
        assertThatThrownBy(() -> validator.validate(file("tiny.pdf", "application/pdf", "%PD")))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    void rejectsFileOverTheSizeLimit() {
        String big = "%PDF-" + "x".repeat(200);
        assertThatThrownBy(() -> validator.validate(file("big.pdf", "application/pdf", big)))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void acceptsFileExactlyAtTheLimit() {
        String exact = "%PDF-" + "x".repeat(95); // 100 bytes
        assertThatCode(() -> validator.validate(file("edge.pdf", "application/pdf", exact)))
                .doesNotThrowAnyException();
    }
}
