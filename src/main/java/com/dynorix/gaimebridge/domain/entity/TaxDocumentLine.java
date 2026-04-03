package com.dynorix.gaimebridge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "tax_document_lines", indexes = {
        @Index(name = "idx_tax_document_lines_document_id", columnList = "document_id")
})
public class TaxDocumentLine {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private TaxDocument document;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "product_code", length = 128)
    private String productCode;

    @Column(name = "product_name", length = 1024)
    private String productName;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "gtin", length = 64)
    private String gtin;

    @Column(name = "unit_code", length = 64)
    private String unitCode;

    @Column(name = "quantity", precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 19, scale = 8)
    private BigDecimal unitPrice;

    @Column(name = "line_amount", precision = 19, scale = 4)
    private BigDecimal lineAmount;

    @Column(name = "vat_rate", precision = 8, scale = 4)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(name = "raw_line_payload", columnDefinition = "text")
    private String rawLinePayload;
}
