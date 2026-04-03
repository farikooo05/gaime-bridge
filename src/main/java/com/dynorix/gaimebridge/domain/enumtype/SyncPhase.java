package com.dynorix.gaimebridge.domain.enumtype;

public enum SyncPhase {
    QUEUED,
    OPENING_LOGIN,
    WAITING_FOR_PHONE_CONFIRMATION,
    WAITING_FOR_COMPANY_SELECTION,
    LOADING_DOCUMENTS,
    PERSISTING_DOCUMENTS,
    COMPLETED,
    FAILED
}
