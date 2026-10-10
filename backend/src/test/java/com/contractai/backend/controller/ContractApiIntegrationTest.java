package com.contractai.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Whole app + real Postgres. @Transactional rolls every test back, leaving no data behind.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContractApiIntegrationTest {

    private static final String USER_HEADER = "X-User-Id";
    private static final String BODY =
            "{\"originalFilename\":\"vendor.pdf\",\"contentType\":\"application/pdf\",\"fileSizeBytes\":2048}";

    @Autowired
    private MockMvc mockMvc;

    private String createContractAs(UUID user) throws Exception {
        String json = mockMvc.perform(post("/api/contracts")
                        .header(USER_HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
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

    @Test
    void createResponseIncludesTimestamps() throws Exception {
        // Regression: createdAt/updatedAt were null in the POST response because the INSERT was deferred.
        mockMvc.perform(post("/api/contracts")
                        .header(USER_HEADER, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createThenGetThenDeleteLifecycle() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = createContractAs(alice);

        mockMvc.perform(get("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        mockMvc.perform(delete("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contracts/" + id).header(USER_HEADER, alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUserCannotReadOrDeleteMyContract() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String id = createContractAs(alice);

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
        createContractAs(alice);
        createContractAs(alice);
        createContractAs(alice);
        createContractAs(bob);

        mockMvc.perform(get("/api/contracts?page=0&size=2").header(USER_HEADER, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
