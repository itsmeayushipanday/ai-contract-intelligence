package com.contractai.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractai.backend.entity.Contract;
import com.contractai.backend.repository.ContractRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Whole app + real Postgres. @Transactional rolls the DB back after each test. Uploaded files go to a
// temp folder (not the real uploads/ folder), which JUnit deletes afterwards.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContractApiIntegrationTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final byte[] PDF_BYTES = "%PDF-1.7 integration test".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.location", () -> storageDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContractRepository repository;

    private MockMultipartFile filePart(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/pdf", content);
    }

    private String uploadAs(UUID user) throws Exception {
        String json = mockMvc.perform(multipart("/api/contracts")
                        .file(filePart("vendor.pdf", PDF_BYTES))
                        .header(USER_HEADER, user))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void uploadedFileIsActuallyOnDiskWithTheSameBytes() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = uploadAs(alice);

        String storagePath = repository.findById(UUID.fromString(id)).orElseThrow().getStoragePath();

        assertThat(storagePath).startsWith(alice + "/").endsWith(".pdf");
        assertThat(Files.readAllBytes(storageDir.resolve(storagePath))).isEqualTo(PDF_BYTES);
    }

    @Test
    void uploadResponseHasTimestampsAndHidesInternalFields() throws Exception {
        // Regression: createdAt/updatedAt were null in the create response because the INSERT was deferred.
        mockMvc.perform(multipart("/api/contracts")
                        .file(filePart("vendor.pdf", PDF_BYTES))
                        .header(USER_HEADER, UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSizeBytes").value(PDF_BYTES.length))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.storagePath").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    @Test
    void fakePdfIsRejectedAndNothingIsStored() throws Exception {
        UUID alice = UUID.randomUUID();

        mockMvc.perform(multipart("/api/contracts")
                        .file(filePart("fake.pdf", "MZ pretend this is a pdf".getBytes(StandardCharsets.US_ASCII)))
                        .header(USER_HEADER, alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid file"));

        assertThat(Files.exists(storageDir.resolve(alice.toString()))).isFalse();
        mockMvc.perform(get("/api/contracts").header(USER_HEADER, alice))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void emptyFileIsRejectedWith400() throws Exception {
        mockMvc.perform(multipart("/api/contracts")
                        .file(filePart("empty.pdf", new byte[0]))
                        .header(USER_HEADER, UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileOverTheLimitIsRejectedWith413AndNothingIsStored() throws Exception {
        UUID alice = UUID.randomUUID();
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF_BYTES, 0, big, 0, PDF_BYTES.length);

        mockMvc.perform(multipart("/api/contracts").file(filePart("big.pdf", big)).header(USER_HEADER, alice))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.title").value("File too large"));

        assertThat(Files.exists(storageDir.resolve(alice.toString()))).isFalse();
    }

    @Test
    void uploadWithoutFilePartReturns400() throws Exception {
        mockMvc.perform(multipart("/api/contracts").header(USER_HEADER, UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadThenGetThenDeleteLifecycle() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = uploadAs(alice);

        mockMvc.perform(get("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        mockMvc.perform(delete("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadReturnsExactlyTheUploadedBytes() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = uploadAs(alice);

        byte[] downloaded = mockMvc.perform(get("/api/contracts/" + id + "/file").header(USER_HEADER, alice))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Length", String.valueOf(PDF_BYTES.length)))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isEqualTo(PDF_BYTES);
    }

    @Test
    void anotherUserCannotDownloadMyFile() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = uploadAs(alice);

        mockMvc.perform(get("/api/contracts/" + id + "/file").header(USER_HEADER, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesTheContractAndItsFileFromDisk() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = uploadAs(alice);
        Path onDisk = storageDir.resolve(repository.findById(UUID.fromString(id)).orElseThrow().getStoragePath());
        assertThat(Files.exists(onDisk)).isTrue();

        mockMvc.perform(delete("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(onDisk)).isFalse();
        mockMvc.perform(get("/api/contracts/" + id + "/file").header(USER_HEADER, alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void failedDeleteByAnotherUserLeavesTheFileOnDisk() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = uploadAs(alice);
        Path onDisk = storageDir.resolve(repository.findById(UUID.fromString(id)).orElseThrow().getStoragePath());

        mockMvc.perform(delete("/api/contracts/" + id).header(USER_HEADER, UUID.randomUUID()))
                .andExpect(status().isNotFound());

        assertThat(Files.exists(onDisk)).isTrue();
    }

    @Test
    void downloadOfOldRecordWithoutFileReturns404() throws Exception {
        UUID alice = UUID.randomUUID();
        Contract legacy = new Contract(); // like the rows created before uploads existed: no storagePath
        legacy.setOwnerId(alice);
        legacy.setOriginalFilename("old.pdf");
        legacy.setContentType("application/pdf");
        legacy.setFileSizeBytes(1);
        UUID id = repository.saveAndFlush(legacy).getId();

        mockMvc.perform(get("/api/contracts/" + id + "/file").header(USER_HEADER, alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("File not available"));
        // and it can still be deleted
        mockMvc.perform(delete("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isNoContent());
    }

    @Test
    void anotherUserCannotReadOrDeleteMyContract() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String id = uploadAs(alice);

        mockMvc.perform(get("/api/contracts/" + id).header(USER_HEADER, bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/contracts/" + id).header(USER_HEADER, bob))
                .andExpect(status().isNotFound());

        // Alice's contract is still there after Bob's failed delete.
        mockMvc.perform(get("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isOk());
    }

    @Test
    void listReturnsOnlyCallersContractsAndHonoursPaging() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        uploadAs(alice);
        uploadAs(alice);
        uploadAs(alice);
        uploadAs(bob);

        mockMvc.perform(get("/api/contracts?page=0&size=2").header(USER_HEADER, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void listWithUnknownSortFieldReturns400NotServerError() throws Exception {
        // Regression: Swagger UI pre-fills sort=string, which is not a column. Was a 500.
        mockMvc.perform(get("/api/contracts?sort=string").header(USER_HEADER, UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort or paging parameter"));
    }

    @Test
    void listAcceptsValidSortField() throws Exception {
        mockMvc.perform(get("/api/contracts?sort=createdAt,desc").header(USER_HEADER, UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    void openApiDocumentDescribesTheContractEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AI Contract Intelligence API"))
                .andExpect(jsonPath("$.paths['/api/contracts']").exists())
                .andExpect(jsonPath("$.paths['/api/contracts/{id}']").exists());
    }
}
