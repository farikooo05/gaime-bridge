package com.dynorix.gaimebridge.service;

import com.dynorix.gaimebridge.dto.DocumentDetailResponse;
import com.dynorix.gaimebridge.dto.DocumentSearchRequest;
import com.dynorix.gaimebridge.dto.DocumentSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TaxDocumentQueryService {

    Page<DocumentSummaryResponse> findDocuments(DocumentSearchRequest request, Pageable pageable);

    DocumentDetailResponse getDocument(UUID id);

    DocumentDetailResponse getDocumentByNumber(String documentNumber);
}
