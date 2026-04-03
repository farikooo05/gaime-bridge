package com.dynorix.gaimebridge.domain.entity;

import com.dynorix.gaimebridge.domain.enumtype.DocumentDirection;
import com.dynorix.gaimebridge.domain.enumtype.DocumentProcessingState;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "tax_documents", indexes = {
        @Index(name = "idx_tax_documents_number", columnList = "document_number"),
        @Index(name = "idx_tax_documents_date", columnList = "document_date"),
        @Index(name = "idx_tax_documents_status", columnList = "portal_status"),
        @Index(name = "idx_tax_documents_seller_tax_id", columnList = "seller_tax_id"),
        @Index(name = "idx_tax_documents_buyer_tax_id", columnList = "buyer_tax_id")
})
public class TaxDocument extends BaseEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "external_document_id", unique = true)
    private String externalDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private DocumentDirection direction;

    @Column(name = "document_series", length = 32)
    private String documentSeries;

    @Column(name = "document_number", nullable = false, length = 64)
    private String documentNumber;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "portal_detail_url", length = 1024)
    private String portalDetailUrl;

    @Column(name = "portal_created_at")
    private OffsetDateTime portalCreatedAt;

    @Column(name = "portal_signed_at")
    private OffsetDateTime portalSignedAt;

    @Column(name = "portal_updated_at")
    private OffsetDateTime portalUpdatedAt;

    @Column(name = "document_type_name", length = 512)
    private String documentTypeName;

    @Column(name = "entry_type_name", length = 128)
    private String entryTypeName;

    @Column(name = "object_name", length = 512)
    private String objectName;

    @Column(name = "document_version_label", length = 128)
    private String documentVersionLabel;

    @Column(name = "amendment_type", length = 256)
    private String amendmentType;

    @Column(name = "related_document_number", length = 128)
    private String relatedDocumentNumber;

    @Column(name = "portal_status", length = 128)
    private String portalStatus;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "seller_name")),
            @AttributeOverride(name = "taxId", column = @Column(name = "seller_tax_id"))
    })
    private PartySnapshot seller = new PartySnapshot();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "buyer_name")),
            @AttributeOverride(name = "taxId", column = @Column(name = "buyer_tax_id"))
    })
    private PartySnapshot buyer = new PartySnapshot();

    @Column(name = "base_note", length = 2048)
    private String baseNote;

    @Column(name = "additional_note", length = 2048)
    private String additionalNote;

    @Column(name = "reason_text", length = 1024)
    private String reasonText;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "AZN";

    @Column(name = "excise_amount", precision = 19, scale = 4)
    private BigDecimal exciseAmount;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "vat_amount", precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "taxable_amount", precision = 19, scale = 4)
    private BigDecimal taxableAmount;

    @Column(name = "non_taxable_amount", precision = 19, scale = 4)
    private BigDecimal nonTaxableAmount;

    @Column(name = "vat_exempt_amount", precision = 19, scale = 4)
    private BigDecimal vatExemptAmount;

    @Column(name = "zero_rated_amount", precision = 19, scale = 4)
    private BigDecimal zeroRatedAmount;

    @Column(name = "road_tax_amount", precision = 19, scale = 4)
    private BigDecimal roadTaxAmount;

    @Column(name = "advance_document_series", length = 32)
    private String advanceDocumentSeries;

    @Column(name = "advance_document_number", length = 64)
    private String advanceDocumentNumber;

    @Column(name = "advance_amount", precision = 19, scale = 4)
    private BigDecimal advanceAmount;

    @Column(name = "total_amount", precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_state", nullable = false, length = 32)
    private DocumentProcessingState processingState = DocumentProcessingState.SUMMARY_LOADED;

    @Column(name = "detail_loaded_at")
    private OffsetDateTime detailLoadedAt;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @OneToOne(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TaxDocumentSnapshot snapshot;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaxDocumentLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Attachment> attachments = new ArrayList<>();

    public void replaceLines(List<TaxDocumentLine> newLines) {
        lines.clear();
        newLines.forEach(this::addLine);
    }

    public void addLine(TaxDocumentLine line) {
        line.setDocument(this);
        lines.add(line);
    }

    public TaxDocumentSnapshot getOrCreateSnapshot() {
        if (snapshot == null) {
            TaxDocumentSnapshot createdSnapshot = new TaxDocumentSnapshot();
            createdSnapshot.setDocument(this);
            snapshot = createdSnapshot;
        }
        return snapshot;
    }
}
