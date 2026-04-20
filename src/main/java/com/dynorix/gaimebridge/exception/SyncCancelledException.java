package com.dynorix.gaimebridge.exception;

/**
 * Exception thrown when a synchronization job is cancelled by the user.
 * Allows the browser automation layer to terminate immediately and gracefully.
 */
public class SyncCancelledException extends RuntimeException {
    public SyncCancelledException(String message) {
        super(message);
    }
}
