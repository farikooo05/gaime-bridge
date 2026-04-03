package com.dynorix.gaimebridge.service;

import com.dynorix.gaimebridge.dto.ExportJobResponse;
import com.dynorix.gaimebridge.dto.ExportRequest;
import java.nio.file.Path;
import java.util.UUID;

public interface ExportService {

    ExportJobResponse createExport(ExportRequest request, String initiatedBy);

    ExportJobResponse getJob(UUID jobId);

    Path resolveExportFile(UUID jobId);
}
