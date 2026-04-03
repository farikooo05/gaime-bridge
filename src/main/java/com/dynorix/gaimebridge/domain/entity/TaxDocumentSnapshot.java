package com.dynorix.gaimebridge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tax_document_snapshots", indexes = {
        @Index(name = "idx_tax_document_snapshots_document_id", columnList = "document_id", unique = true)
})
public class TaxDocumentSnapshot extends BaseEntity {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private TaxDocument document;

    @Column(name = "source_row_number")
    private Integer sourceRowNumber;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @Column(name = "parsed_payload", columnDefinition = "text")
    private String parsedPayload;

    @Column(name = "html_snapshot_path", length = 1024)
    private String htmlSnapshotPath;

    @Column(name = "extracted_at")
    private OffsetDateTime extractedAt;

    @Column(name = "parser_version", length = 64)
    private String parserVersion;
}
