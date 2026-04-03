package com.dynorix.gaimebridge.application.port;

import com.dynorix.gaimebridge.domain.enumtype.SyncPhase;

@FunctionalInterface
public interface SyncProgressListener {

    void onPhase(SyncPhase phase, String message);
}
