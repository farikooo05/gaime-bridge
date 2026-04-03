package com.dynorix.gaimebridge.dto;

import com.dynorix.gaimebridge.domain.enumtype.ExportFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ExportRequest(
        @NotNull ExportFormat format,
        @NotEmpty List<UUID> documentIds
) {
}
