package com.dynorix.gaimebridge.dto;

import jakarta.validation.constraints.NotBlank;

public record TaxpayerDto(
        @NotBlank String legalTin,
        String companyName
) {
}
