package com.contractai.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.enums.ContractStatus;
import com.contractai.backend.exception.ContractNotFoundException;
import com.contractai.backend.service.ContractService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Loads only the web layer. The service is a fake, so this tests URLs, status codes and JSON.
@WebMvcTest(ContractController.class)
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

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(service.create(any(), any())).thenReturn(sample());

        mockMvc.perform(post("/api/contracts")
                        .header(USER_HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalFilename\":\"vendor.pdf\",\"contentType\":\"application/pdf\",\"fileSizeBytes\":2048}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/contracts/" + contractId))
                .andExpect(jsonPath("$.originalFilename").value("vendor.pdf"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    @Test
    void createRejectsInvalidBodyWith400() throws Exception {
        mockMvc.perform(post("/api/contracts")
                        .header(USER_HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalFilename\":\"\",\"contentType\":\"application/pdf\",\"fileSizeBytes\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.originalFilename").isNotEmpty())
                .andExpect(jsonPath("$.errors.fileSizeBytes").isNotEmpty())
                .andExpect(jsonPath("$.errors.contentType").doesNotExist());
    }

    @Test
    void createRejectsMalformedJsonWith400() throws Exception {
        mockMvc.perform(post("/api/contracts")
                        .header(USER_HEADER, user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
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

    @Test
    void deleteReturns204AndCallsServiceWithCallerAndId() throws Exception {
        mockMvc.perform(delete("/api/contracts/" + contractId).header(USER_HEADER, user))
                .andExpect(status().isNoContent());

        verify(service).delete(user, contractId);
    }
}
