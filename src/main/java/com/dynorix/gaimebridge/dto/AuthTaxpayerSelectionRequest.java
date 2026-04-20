package com.dynorix.gaimebridge.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthTaxpayerSelectionRequest(
        @NotBlank(message = "sessionId is required") String sessionId,
        @NotBlank(message = "legalTin is required") String legalTin
) {
}
