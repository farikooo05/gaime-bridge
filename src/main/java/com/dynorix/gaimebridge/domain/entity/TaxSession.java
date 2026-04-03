package com.dynorix.gaimebridge.domain.entity;

import com.dynorix.gaimebridge.domain.enumtype.SessionStatus;
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
@Table(name = "tax_sessions")
public class TaxSession extends BaseEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "username", nullable = false, length = 128)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 32)
    private SessionStatus sessionStatus;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "last_activity_at")
    private OffsetDateTime lastActivityAt;

    @Column(name = "auth_metadata", columnDefinition = "text")
    private String authMetadata;

    @Column(name = "error_message", length = 2048)
    private String errorMessage;
}
