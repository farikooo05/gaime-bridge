package com.dynorix.gaimebridge.domain.entity;

import com.dynorix.gaimebridge.domain.enumtype.LogType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "integration_logs")
public class IntegrationLog extends BaseEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false, length = 32)
    private LogType logType;

    @Column(name = "source_system", length = 128)
    private String sourceSystem;

    @Column(name = "operation_name", nullable = false, length = 128)
    private String operationName;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "status", length = 64)
    private String status;

    @Column(name = "message", nullable = false, length = 1024)
    private String message;

    @Column(name = "details", columnDefinition = "text")
    private String details;
}
