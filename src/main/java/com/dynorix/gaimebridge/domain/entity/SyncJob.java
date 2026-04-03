package com.dynorix.gaimebridge.domain.entity;

import com.dynorix.gaimebridge.domain.enumtype.JobStatus;
import com.dynorix.gaimebridge.domain.enumtype.SyncPhase;
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
@Table(name = "sync_jobs")
public class SyncJob extends BaseEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 64)
    private SyncPhase phase = SyncPhase.QUEUED;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(name = "filters_json", columnDefinition = "text")
    private String filtersJson;

    @Column(name = "documents_discovered")
    private Integer documentsDiscovered = 0;

    @Column(name = "documents_persisted")
    private Integer documentsPersisted = 0;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "phase_message", length = 2048)
    private String phaseMessage;
}
