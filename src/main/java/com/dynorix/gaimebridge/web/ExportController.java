package com.dynorix.gaimebridge.web;

import com.dynorix.gaimebridge.dto.ExportJobResponse;
import com.dynorix.gaimebridge.dto.ExportRequest;
import com.dynorix.gaimebridge.service.ExportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    public ExportJobResponse createExport(@Valid @RequestBody ExportRequest request, Principal principal) {
        return exportService.createExport(request, principal.getName());
    }

    @GetMapping("/{jobId}")
    public ExportJobResponse getExport(@PathVariable UUID jobId) {
        return exportService.getJob(jobId);
    }

    @GetMapping("/{jobId}/file")
    public ResponseEntity<Resource> downloadExport(@PathVariable UUID jobId) throws IOException {
        Path exportPath = exportService.resolveExportFile(jobId);
        Resource resource = new FileSystemResource(exportPath);
        String contentType = Files.probeContentType(exportPath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_JSON_VALUE;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportPath.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
