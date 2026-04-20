package com.dynorix.gaimebridge.integration.taxportal;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class PlaywrightAuthSessionManager {

    private final Map<String, AuthSessionContext> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictor = Executors.newSingleThreadScheduledExecutor();

    public PlaywrightAuthSessionManager() {
        // Evict stale sessions every minute
        evictor.scheduleAtFixedRate(() -> {
            activeSessions.values().stream()
                    .filter(session -> session.createdAt().isBefore(Instant.now().minus(3, ChronoUnit.MINUTES)))
                    .forEach(this::closeSession);
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void registerSession(AuthSessionContext session) {
        activeSessions.put(session.sessionId(), session);
    }

    public Optional<AuthSessionContext> getSession(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    public void closeSession(AuthSessionContext session) {
        activeSessions.remove(session.sessionId());
        session.close();
    }
}
