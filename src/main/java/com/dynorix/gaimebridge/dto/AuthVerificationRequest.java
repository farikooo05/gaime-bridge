package com.dynorix.gaimebridge.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthVerificationRequest(
        @NotBlank(message = "portalPhone is required") String portalPhone,
        @NotBlank(message = "portalUserId is required") String portalUserId
) {
}
