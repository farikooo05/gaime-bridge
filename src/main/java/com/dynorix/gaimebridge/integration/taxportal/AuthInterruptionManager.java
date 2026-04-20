package com.dynorix.gaimebridge.integration.taxportal;

import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active authentication threads and Playwright instances to allow for global cancellation.
 */
@Component
public class AuthInterruptionManager {
    private static final Logger log = LoggerFactory.getLogger(AuthInterruptionManager.class);

    // Map of SessionId -> Thread/Playwright to allow for targeted interruption
    // For now, we use a simple singleton-like approach for the current active auth if SessionId is unknown
    private final Map<String, AuthContext> activeAuths = new ConcurrentHashMap<>();
    private volatile Thread currentAuthThread;
    private volatile Playwright currentPlaywright;

    public void register(Thread thread, Playwright playwright) {
        this.currentAuthThread = thread;
        this.currentPlaywright = playwright;
        log.debug("Registered active auth thread: {}", thread.getName());
    }

    public void updatePlaywright(Playwright playwright) {
        this.currentPlaywright = playwright;
        log.debug("Updated active auth context with Playwright instance.");
    }

    public void unregister() {
        this.currentAuthThread = null;
        this.currentPlaywright = null;
        log.debug("Unregistered active auth thread.");
    }

    public void cancelAll() {
        log.info("Requested global cancellation of all active Playwright authentication sessions.");
        
        if (currentPlaywright != null) {
            try {
                log.info("Closing active Playwright instance...");
                currentPlaywright.close();
            } catch (Exception e) {
                log.warn("Error closing Playwright during cancellation: {}", e.getMessage());
            }
        }

        if (currentAuthThread != null) {
            log.info("Interrupting active auth thread: {}", currentAuthThread.getName());
            currentAuthThread.interrupt();
        }
        
        unregister();
    }

    private record AuthContext(Thread thread, Playwright playwright) {}
}
