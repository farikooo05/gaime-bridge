package com.dynorix.gaimebridge.repository;

import com.dynorix.gaimebridge.domain.entity.Attachment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
}
