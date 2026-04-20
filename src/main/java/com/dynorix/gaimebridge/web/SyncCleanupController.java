package com.dynorix.gaimebridge.web;

import com.dynorix.gaimebridge.repository.TaxDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for managing local data cleanup.
 * Allows users to wipe sync history to verify fresh sync runs.
 */
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Slf4j
public class SyncCleanupController {

    private final TaxDocumentRepository repository;

    @DeleteMapping("/clear")
    @Transactional
    public ResponseEntity<?> clearAllData() {
        log.info("USER REQUEST: Clearing all local document data from the database.");
        long count = repository.count();
        repository.deleteAll();
        log.info("Local database cleared. Removed {} documents.", count);
        return ResponseEntity.ok(Map.of(
                "message", "Successfully cleared all " + count + " documents from local history.",
                "clearedCount", count
        ));
    }
}
