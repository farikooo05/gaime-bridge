package com.dynorix.gaimebridge.web;

import com.dynorix.gaimebridge.dto.DocumentDetailResponse;
import com.dynorix.gaimebridge.dto.DocumentSearchRequest;
import com.dynorix.gaimebridge.dto.DocumentSummaryResponse;
import com.dynorix.gaimebridge.dto.PageResponse;
import com.dynorix.gaimebridge.service.TaxDocumentQueryService;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final TaxDocumentQueryService taxDocumentQueryService;

    public DocumentController(TaxDocumentQueryService taxDocumentQueryService) {
        this.taxDocumentQueryService = taxDocumentQueryService;
    }

    @GetMapping
    public PageResponse<DocumentSummaryResponse> getDocuments(
            @ModelAttribute DocumentSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        Page<DocumentSummaryResponse> result = taxDocumentQueryService.findDocuments(
                request,
                PageRequest.of(normalizedPage, normalizedSize));
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public DocumentDetailResponse getDocument(@PathVariable UUID id) {
        return taxDocumentQueryService.getDocument(id);
    }

    @GetMapping("/by-number/{documentNumber}")
    public DocumentDetailResponse getDocumentByNumber(@PathVariable String documentNumber) {
        return taxDocumentQueryService.getDocumentByNumber(documentNumber);
    }
}
