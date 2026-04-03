package com.dynorix.gaimebridge.dto;

import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import java.time.LocalDate;

public record DocumentSearchRequest(
        String documentNumber,
        String counterpartyTaxId,
        String portalStatus,
        DocumentDirection direction,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
