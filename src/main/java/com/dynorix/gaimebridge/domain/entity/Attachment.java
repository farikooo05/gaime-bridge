package com.dynorix.gaimebridge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachments_document_id", columnList = "document_id")
})
public class Attachment {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private TaxDocument document;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_type", length = 64)
    private String fileType;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "downloaded_at")
    private OffsetDateTime downloadedAt;
}
