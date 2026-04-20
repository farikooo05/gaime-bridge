package com.dynorix.gaimebridge.dto;

import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SyncRequest(
        @NotBlank String portalPhone,
        @NotBlank String portalUserId,
        DocumentDirection direction,
        @NotNull LocalDate dateFrom,
        @NotNull LocalDate dateTo,
        Boolean loadDocumentDetails,
        Boolean dryRun
) {
}
