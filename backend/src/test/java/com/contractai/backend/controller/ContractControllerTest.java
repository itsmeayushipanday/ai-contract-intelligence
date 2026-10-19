package com.contractai.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractai.backend.config.UploadProperties;
import com.contractai.backend.dto.ContractDownload;
import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.enums.ContractStatus;
import com.contractai.backend.exception.ContractNotFoundException;
import com.contractai.backend.exception.FileTooLargeException;
import com.contractai.backend.exception.InvalidFileException;
import com.contractai.backend.exception.StoredFileNotFoundException;
import com.contractai.backend.service.ContractService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Loads only the web layer. The service is a fake, so this tests URLs, status codes and JSON.
// The slice does not scan @ConfigurationProperties, and GlobalExceptionHandler needs UploadProperties.
@WebMvcTest(ContractController.class)
@EnableConfigurationProperties(UploadProperties.class)
class ContractControllerTest {

    private static final String USER_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContractService service;

    private final UUID user = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();

    private ContractResponse sample() {
        return new ContractResponse(contractId, "vendor.pdf", "application/pdf", 2048,
                ContractStatus.UPLOADED, null, null, Instant.now(), Instant.now());
    }

    private MockMultipartFile pdfPart() {
        return new MockMultipartFile("file", "vendor.pdf", "application/pdf", "%PDF-1.7".getBytes());
    }

    @Test
    void uploadReturns201WithLocationAndBodyWithoutInternalFields() throws Exception {
        when(service.upload(any(), any())).thenReturn(sample());

        mockMvc.perform(multipart("/api/contracts").file(pdfPart()).header(USER_HEADER, user))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/contracts/" + contractId))
                .andExpect(jsonPath("$.originalFilename").value("vendor.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    @Test
    void uploadWithoutFilePartReturns400() throws Exception {
        mockMvc.perform(multipart("/api/contracts").header(USER_HEADER, user))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void uploadWithJsonBodyInsteadOfMultipartReturns415() throws Exception {
        mockMvc.perform(post("/api/contracts")
                        .header(USER_HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalFilename\":\"x.pdf\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void invalidFileReturns400ProblemDetail() throws Exception {
        when(service.upload(any(), any())).thenThrow(new InvalidFileException("The file is not a valid PDF."));

        mockMvc.perform(multipart("/api/contracts").file(pdfPart()).header(USER_HEADER, user))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid file"))
                .andExpect(jsonPath("$.detail").value("The file is not a valid PDF."));
    }

    @Test
    void tooLargeFileReturns413ProblemDetail() throws Exception {
        when(service.upload(any(), any())).thenThrow(new FileTooLargeException("The file exceeds the maximum size."));

        mockMvc.perform(multipart("/api/contracts").file(pdfPart()).header(USER_HEADER, user))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.title").value("File too large"));
    }

    @Test
    void missingUserHeaderReturns400() throws Exception {
        mockMvc.perform(get("/api/contracts/" + contractId))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void malformedUserIdOrContractIdReturns400() throws Exception {
        mockMvc.perform(get("/api/contracts/" + contractId).header(USER_HEADER, "not-a-uuid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/contracts/not-a-uuid").header(USER_HEADER, user))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturns404WhenServiceSaysNotFound() throws Exception {
        when(service.get(user, contractId)).thenThrow(new ContractNotFoundException(contractId));

        mockMvc.perform(get("/api/contracts/" + contractId).header(USER_HEADER, user))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Contract not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingInternals() throws Exception {
        when(service.get(user, contractId))
                .thenThrow(new IllegalStateException("could not connect to jdbc:postgresql://secret-host"));

        mockMvc.perform(get("/api/contracts/" + contractId).header(USER_HEADER, user))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("secret-host"))));
    }

    private ContractDownload downloadOf(String filename, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new ContractDownload(filename, bytes.length, new ByteArrayInputStream(bytes));
    }

    @Test
    void downloadStreamsPdfWithSafeHeaders() throws Exception {
        when(service.download(user, contractId)).thenReturn(downloadOf("vendor.pdf", "%PDF-1.7 body"));

        mockMvc.perform(get("/api/contracts/" + contractId + "/file").header(USER_HEADER, user))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Length", "13"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("vendor.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("%PDF-1.7 body"));
    }

    @Test
    void downloadEscapesQuotesInTheFilename() throws Exception {
        when(service.download(user, contractId)).thenReturn(downloadOf("a\"b; evil=1.pdf", "%PDF-1.7"));

        mockMvc.perform(get("/api/contracts/" + contractId + "/file").header(USER_HEADER, user))
                .andExpect(status().isOk())
                // the quote must be escaped, so the filename cannot break out of its quoted value
                .andExpect(header().string("Content-Disposition", containsString("a\\\"b")));
    }

    @Test
    void downloadReturns404WhenNotTheCallersContract() throws Exception {
        when(service.download(user, contractId)).thenThrow(new ContractNotFoundException(contractId));

        mockMvc.perform(get("/api/contracts/" + contractId + "/file").header(USER_HEADER, user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Contract not found"));
    }

    @Test
    void downloadReturns404ProblemDetailWhenFileIsNotAvailable() throws Exception {
        when(service.download(user, contractId)).thenThrow(new StoredFileNotFoundException("missing on disk: secret/path.pdf"));

        mockMvc.perform(get("/api/contracts/" + contractId + "/file").header(USER_HEADER, user))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("File not available"))
                .andExpect(content().string(not(containsString("secret/path.pdf"))));
    }

    @Test
    void deleteReturns204AndCallsServiceWithCallerAndId() throws Exception {
        mockMvc.perform(delete("/api/contracts/" + contractId).header(USER_HEADER, user))
                .andExpect(status().isNoContent());

        verify(service).delete(user, contractId);
    }
}
