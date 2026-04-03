package com.dynorix.gaimebridge.domain.entity;

import com.dynorix.gaimebridge.domain.enumtype.ExportFormat;
import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "export_jobs")
public class ExportJob extends BaseEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 16)
    private ExportFormat outputFormat;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "document_count")
    private Integer documentCount = 0;

    @Column(name = "output_path", length = 1024)
    private String outputPath;

    @Column(name = "filters_json", columnDefinition = "text")
    private String filtersJson;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;
}
