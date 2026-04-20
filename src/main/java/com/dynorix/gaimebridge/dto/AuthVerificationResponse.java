package com.dynorix.gaimebridge.dto;

import java.util.List;

public record AuthVerificationResponse(
        String sessionId,
        List<TaxpayerDto> taxpayers
) {
}
