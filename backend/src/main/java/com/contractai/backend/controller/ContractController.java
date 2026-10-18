package com.contractai.backend.controller;

import com.contractai.backend.dto.ContractResponse;
import com.contractai.backend.dto.PageResponse;
import com.contractai.backend.service.ContractService;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP layer only: parse the request, call the service, shape the response.
 *
 * TEMPORARY: the caller identifies itself with the X-User-Id header. This is NOT secure
 * (anyone can send any id). Phase 11 replaces it with the user taken from a verified JWT.
 */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private static final String USER_HEADER = "X-User-Id";

    private final ContractService service;

    /** multipart/form-data upload: the PDF goes in a part named "file". */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractResponse> upload(
            @RequestHeader(USER_HEADER) UUID userId,
            @RequestPart("file") MultipartFile file) {
        ContractResponse created = service.upload(userId, file);
        return ResponseEntity.created(URI.create("/api/contracts/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<ContractResponse> list(
            @RequestHeader(USER_HEADER) UUID userId, @ParameterObject Pageable pageable) {
        return service.list(userId, pageable);
    }

    @GetMapping("/{id}")
    public ContractResponse get(@RequestHeader(USER_HEADER) UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_HEADER) UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
