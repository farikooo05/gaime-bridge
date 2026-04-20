package com.dynorix.gaimebridge.dto;

import java.time.OffsetDateTime;

/**
 * Represents the current status of the tax portal authentication session.
 */
public record AuthStatusResponse(
    boolean authenticated,
    String companyName,
    String legalTin,
    OffsetDateTime expiresAt,
    String portalPhone
) {}
