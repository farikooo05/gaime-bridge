/**
 * DYNORIX OPERATOR UI - CORE LOGIC
 * High-performance, Vanilla JS implementation for Azerbaijan Tax Portal synchronization.
 */

const state = {
    authHeader: `Basic ${btoa("dev:dev123")}`,
    documents: [],
    selectedIds: new Set(),
    activeDocumentId: null,
    exportJob: null,
    uiStep: "credentials",
    syncJob: null,
    syncPollHandle: null,
    authSessionId: null,
    session: {
        active: false,
        loading: true,
        companyName: null,
        expiresAt: null
    },
    availableTaxpayers: [],
    exportColumns: [],
    pagination: {
        total: 0,
        page: 0,
        size: 50,
        totalPages: 0
    }
};

const elements = {
    portalForm: document.getElementById("portal-form"),
    filtersForm: document.getElementById("filters-form"),
    connectButton: document.getElementById("connect-button"),
    statusBanner: document.getElementById("status-banner"),
    journeyNote: document.getElementById("journey-note"),
    steps: Array.from(document.querySelectorAll(".step")),
    documentsSection: document.getElementById("documents-section"),
    refreshButton: document.getElementById("refresh-button"),
    clearFiltersButton: document.getElementById("clear-filters-button"),
    documentsBody: document.getElementById("documents-body"),
    documentsCount: document.getElementById("documents-count"),
    selectedCount: document.getElementById("selected-count"),
    selectVisibleButton: document.getElementById("select-visible-button"),
    clearSelectionButton: document.getElementById("clear-selection-button"),
    exportButton: document.getElementById("export-button"),
    documentPreview: document.getElementById("document-preview"),
    exportResult: document.getElementById("export-result"),
    exportFormat: document.getElementById("export-format"),
    dateFrom: document.getElementById("date-from"),
    dateTo: document.getElementById("date-to"),
    connectionStatus: document.getElementById("connection-status"),
    stopSyncButton: document.getElementById("stop-sync-button"),
    stopSyncButtonAlt: document.getElementById("stop-sync-button-alt"),

    // Modal elements
    taxpayerModal: document.getElementById("taxpayer-modal"),
    taxpayerList: document.getElementById("taxpayer-list"),
    closeModalButton: document.getElementById("close-modal-button"),
    clearHistoryButton: document.getElementById("clear-history-button"),

    // Column Modal
    columnModal: document.getElementById("column-modal"),
    exportSettingsButton: document.getElementById("export-settings-button"),
    saveColumnsButton: document.getElementById("save-columns-button"),
    resetColumnsButton: document.getElementById("reset-columns-button"),

    // Pagination elements
    paginationRange: document.getElementById("pagination-range"),
    paginationTotal: document.getElementById("pagination-total"),
    prevPageButton: document.getElementById("prev-page-button"),
    nextPageButton: document.getElementById("next-page-button"),
    currentPageLabel: document.getElementById("current-page-label"),
    sessionLife: document.getElementById("session-life"),
    columnCheckboxes: () => document.querySelectorAll('#column-modal input[type="checkbox"]'),

    // Confirm Modal
    confirmModal: document.getElementById("confirm-modal"),
    cancelConfirmButton: document.getElementById("cancel-confirm-button"),
    executeConfirmButton: document.getElementById("execute-confirm-button"),
    // Logout
    logoutButton: document.getElementById("logout-button")
};

/**
 * RECOVERY / CONCURRENCY CHECK
 */
async function checkActiveSync() {
    try {
        const dummyPayload = {
            portalPhone: "000000000",
            portalUserId: "000000",
            dateFrom: new Date().toISOString().split('T')[0],
            dateTo: new Date().toISOString().split('T')[0],
            loadDocumentDetails: false,
            dryRun: true
        };
        const response = await apiFetch("/api/v1/sync/documents", {
            method: "POST",
            body: JSON.stringify(dummyPayload),
            headers: { "Content-Type": "application/json" }
        });
        // The startSync endpoint throws error if one is running, but let's just refresh the state.
    } catch (error) {
        if (error.message.includes("Another sync job is already active")) {
            const match = error.message.match(/active: ([a-f0-9-]+)/);
            if (match) {
                const jobId = match[1];
                setConnectionStatus("Resuming...", "warn");
                elements.connectButton.disabled = true;
                toggleStopButtons(true);
                pollSyncJob(jobId);
            }
        }
    }
}

/**
 * INITIALIZATION
 */
async function initialize() {
    // Prevent interaction until session status is known
    elements.connectButton.disabled = true;
    elements.connectButton.innerText = "Checking...";

    initializeDefaultDates();
    renderDocuments();
    renderExportResult();
    updateSelectionMetrics();
    loadColumnPreferences();
    setupEventListeners();
    
    // Initial data load from local database
    await loadDocuments();
    
    // Background checks
    await checkSessionStatus();
    await checkActiveSync();
}

function initializeDefaultDates() {
    const today = new Date();
    const isoToday = today.toISOString().slice(0, 10);
    const start = new Date(today);
    start.setMonth(start.getMonth() - 1);
    const isoStart = start.toISOString().slice(0, 10);
    elements.dateFrom.value = isoStart;
    elements.dateTo.value = isoToday;
}

/**
 * UI UTILITIES
 */
function setBanner(message, tone = "neutral") {
    elements.statusBanner.className = `status-banner ${tone}`;
    elements.statusBanner.textContent = message;
}

function setStep(step, note, tone = "neutral") {
    state.uiStep = step;
    elements.steps.forEach((element) => {
        const isActive = element.dataset.step === step;
        const isDone = stepOrderIndex(element.dataset.step) < stepOrderIndex(step);
        element.classList.toggle("active", isActive);
        element.classList.toggle("done", isDone);
    });
    elements.journeyNote.textContent = note;
    setBanner(note, tone);
}

function stepOrderIndex(step) {
    const order = ["credentials", "phone-confirmation", "company-selection", "syncing", "documents-ready"];
    return order.indexOf(step);
}

/**
 * SESSION MANAGEMENT
 */
async function checkSessionStatus() {
    state.session.loading = true;
    elements.connectButton.disabled = true;
    elements.connectButton.innerText = "Checking Session...";

    try {
        const response = await apiFetch("/api/v1/auth/status", { skip401Retry: true });
        const data = await response.json();

        state.session.active = data.authenticated;
        state.session.companyName = data.companyName;
        state.session.expiresAt = data.expiresAt;

        if (data.authenticated) {
            setConnectionStatus(`Connected (${data.companyName})`, "ok");
            elements.journeyNote.innerText = `Using existing session for ${data.companyName}. Click 'Fast Sync' to start.`;
            elements.connectButton.innerText = "Fast Sync";
            elements.logoutButton.classList.remove("hidden");
            if (data.portalPhone) {
                document.getElementById("portal-phone").value = data.portalPhone;
            }
            startSessionLifeTimer();
        } else {
            setConnectionStatus("Disconnected", "muted");
            elements.connectButton.innerText = "Connect & Sync";
            elements.sessionLife.innerText = "N/A";
            elements.logoutButton.classList.add("hidden");
            stopSessionLifeTimer();
        }
    } catch (e) {
        console.warn("Unable to check session status", e);
        elements.connectButton.innerText = "Connect & Sync";
        elements.sessionLife.innerText = "N/A";
        stopSessionLifeTimer();
    } finally {
        state.session.loading = false;
        elements.connectButton.disabled = false;
    }
}

let sessionTimerHandle = null;

function startSessionLifeTimer() {
    stopSessionLifeTimer();
    updateSessionLifeDisplay();
    sessionTimerHandle = setInterval(updateSessionLifeDisplay, 10000); // Update every 10s
}

function stopSessionLifeTimer() {
    if (sessionTimerHandle) {
        clearInterval(sessionTimerHandle);
        sessionTimerHandle = null;
    }
}

function updateSessionLifeDisplay() {
    if (!state.session.active || !state.session.expiresAt) {
        elements.sessionLife.innerText = "N/A";
        elements.sessionLife.style.color = "inherit";
        return;
    }

    const expiry = new Date(state.session.expiresAt).getTime();
    const now = new Date().getTime();
    const diff = expiry - now;

    if (diff <= 0) {
        elements.sessionLife.innerText = "Expired";
        elements.sessionLife.style.color = "var(--error)";
        state.session.active = false;
        elements.connectButton.innerText = "Re-connect & Sync";
        setConnectionStatus("Session Expired", "error");
        elements.logoutButton.classList.add("hidden");
        stopSessionLifeTimer();
        return;
    }

    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));

    elements.sessionLife.innerText = `${hours}h ${minutes}m`;

    if (diff < 15 * 60 * 1000) { // Less than 15 mins
        elements.sessionLife.style.color = "var(--error)";
    } else if (diff < 60 * 60 * 1000) { // Less than 1 hour
        elements.sessionLife.style.color = "var(--warn)";
    } else {
        elements.sessionLife.style.color = "var(--ok)";
    }
}

async function handleLogout() {
    if (!confirm("Start a new 8-hour session? (This will clear your current ASAN login state)")) {
        return;
    }
    
    setBanner("Clearing session...", "warn");
    try {
        await apiFetch("/api/v1/auth/logout", { method: "POST" });
        await checkSessionStatus();
        setBanner("Session cleared. Please log in again for a fresh 8-hour window.", "success");
    } catch (e) {
        setBanner("Logout failed: " + e.message, "error");
    }
}

function setConnectionStatus(status, tone = "muted") {
    elements.connectionStatus.textContent = status;
    elements.connectionStatus.style.color = tone === "ok" ? "var(--ok)" : tone === "warn" ? "var(--warn)" : tone === "error" ? "var(--error)" : "var(--accent)";
}

function toggleStopButtons(visible) {
    if (visible) {
        elements.stopSyncButton.classList.remove("hidden");
        elements.stopSyncButton.disabled = false;
        if (elements.stopSyncButtonAlt) {
            elements.stopSyncButtonAlt.classList.remove("hidden");
            elements.stopSyncButtonAlt.disabled = false;
        }
    } else {
        elements.stopSyncButton.classList.add("hidden");
        if (elements.stopSyncButtonAlt) {
            elements.stopSyncButtonAlt.classList.add("hidden");
        }
    }
}

/**
 * PREFERENCES
 */
function loadColumnPreferences() {
    const saved = localStorage.getItem("gaime_export_columns");
    if (saved) {
        state.exportColumns = JSON.parse(saved);
        // Sync checkboxes
        elements.columnCheckboxes().forEach(cb => {
            cb.checked = state.exportColumns.includes(cb.value);
        });
    } else {
        // Default: all checked
        state.exportColumns = Array.from(elements.columnCheckboxes()).map(cb => cb.value);
    }
}

function saveColumnPreferences() {
    state.exportColumns = Array.from(elements.columnCheckboxes())
        .filter(cb => cb.checked)
        .map(cb => cb.value);

    localStorage.setItem("gaime_export_columns", JSON.stringify(state.exportColumns));
    elements.columnModal.classList.add("hidden");
    setBanner("Export preferences saved.", "success");
}

function resetColumnPreferences() {
    elements.columnCheckboxes().forEach(cb => cb.checked = true);
    saveColumnPreferences();
}

/**
 * API UTILITIES
 */
async function apiFetch(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.authHeader) {
        headers.set("Authorization", state.authHeader);
    }
    const response = await fetch(path, {
        ...options,
        headers
    });

    if (response.status === 401 && !options.skip401Retry) {
        console.warn("Token expired (401). Triggering session re-check.");
        await checkSessionStatus(); // This will update UI to "Expired" mode
        throw new Error("Portal session expired. Please re-authenticate.");
    }

    if (!response.ok) {
        let message = `Request failed with status ${response.status}`;
        try {
            const body = await response.json();
            if (body.message) message = body.message;
        } catch (_) { }
        throw new Error(message);
    }
    return response;
}

/**
 * AUTH FLOW
 */
async function startAuthFlow() {
    const formData = new FormData(elements.portalForm);
    const payload = {
        portalPhone: formData.get("portalPhone"),
        portalUserId: formData.get("portalUserId")
    };

    setStep("phone-confirmation", "Check your phone for the ASAN Imza confirmation code...", "neutral");
    setConnectionStatus("Verifying...", "warn");
    elements.connectButton.disabled = true;
    toggleStopButtons(true);

    try {
        const response = await apiFetch("/api/v1/auth/start-verification", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const result = await response.json();

        state.authSessionId = result.sessionId;
        state.availableTaxpayers = result.taxpayers || [];

        if (state.availableTaxpayers.length === 0) {
            throw new Error("No taxpayers found on this account. Please check the portal manually.");
        }

        showTaxpayerPicker();
        setStep("company-selection", "Authentication successful. Choose your company to finalize.", "success");
        setConnectionStatus("Wait Selection", "warn");
        toggleStopButtons(false);
    } catch (error) {
        setBanner(error.message, "error");
        setConnectionStatus("Failed", "error");
        elements.connectButton.disabled = false;
        toggleStopButtons(false);
        throw error;
    }
}

function showTaxpayerPicker() {
    elements.taxpayerList.innerHTML = state.availableTaxpayers.map(tp => `
        <div class="taxpayer-item" data-tin="${tp.legalTin}">
            <strong>${escapeHtml(tp.companyName)}</strong>
            <span>VÖEN: ${tp.legalTin}</span>
        </div>
    `).join("");

    elements.taxpayerModal.classList.remove("hidden");

    elements.taxpayerList.querySelectorAll(".taxpayer-item").forEach(item => {
        item.onclick = () => confirmSelection(item.dataset.tin);
    });
}

function hideTaxpayerPicker() {
    elements.taxpayerModal.classList.add("hidden");
}

async function confirmSelection(tin) {
    hideTaxpayerPicker();
    setStep("company-selection", "Finalizing portal session...", "neutral");
    setConnectionStatus("Finalizing...", "warn");

    try {
        await apiFetch("/api/v1/auth/confirm-taxpayer", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                sessionId: state.authSessionId,
                legalTin: tin
            })
        });

        setBanner("Session established. Starting document sync...", "success");
        state.session.active = true;
        state.session.loading = false;
        elements.connectButton.innerText = "Fast Sync";
        setConnectionStatus("Connected", "ok");
        elements.connectButton.disabled = true;
        toggleStopButtons(true);

        const formData = new FormData(elements.portalForm);
        await startPortalSync(formData);
    } catch (error) {
        setBanner("Selection failed: " + error.message, "error");
        setConnectionStatus("Error", "error");
        elements.connectButton.disabled = false;
    }
}

/**
 * SYNC FLOW
 */
async function startPortalSync(formData) {
    state.exportJob = null;
    stopSyncPolling();
    renderExportResult();
    state.selectedIds.clear();
    state.activeDocumentId = null;
    elements.documentPreview.innerHTML = "No document selected.";

    setStep("syncing", "Connecting to portal API for document discovery...", "neutral");

    const payload = {
        portalPhone: formData.get("portalPhone"),
        portalUserId: formData.get("portalUserId"),
        dateFrom: formData.get("dateFrom"),
        dateTo: formData.get("dateTo"),
        loadDocumentDetails: formData.get("loadDocumentDetails") === "on"
    };

    try {
        const response = await apiFetch("/api/v1/sync/documents", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        state.syncJob = await response.json();
        toggleStopButtons(true);
        elements.connectButton.disabled = true;
        await pollSyncJob(state.syncJob.id);
    } catch (error) {
        setBanner(error.message, "error");
        elements.connectButton.disabled = false;
    }
}

async function pollSyncJob(jobId) {
    try {
        const response = await apiFetch(`/api/v1/sync/jobs/${jobId}`);
        const syncJob = await response.json();
        state.syncJob = syncJob;

        const mapped = mapPhaseToStep(syncJob);
        setStep(mapped.step, mapped.note, syncJob.status === "FAILED" ? "error" : syncJob.status === "COMPLETED" ? "success" : "neutral");

        if (syncJob.status === "COMPLETED") {
            stopSyncPolling();
            elements.documentsSection.classList.remove("hidden");
            elements.connectButton.disabled = false;
            toggleStopButtons(false);
            await loadDocuments();
            return;
        }

        if (syncJob.status === "FAILED" || syncJob.status === "CANCELLED") {
            stopSyncPolling();
            elements.connectButton.disabled = false;
            toggleStopButtons(false);
            if (syncJob.status === "CANCELLED") {
                setBanner("Synchronization stopped.", "warn");
                setConnectionStatus("Stopped", "accent");
            }
            return;
        }

        toggleStopButtons(true);
        elements.connectButton.disabled = true;
        state.syncPollHandle = setTimeout(() => pollSyncJob(jobId), 1500);
    } catch (error) {
        stopSyncPolling();
        setBanner(error.message, "error");
        elements.connectButton.disabled = false;
        toggleStopButtons(false);
    }
}

function stopSyncPolling() {
    if (state.syncPollHandle) {
        clearTimeout(state.syncPollHandle);
        state.syncPollHandle = null;
    }
}

function mapPhaseToStep(syncJob) {
    switch (syncJob.phase) {
        case "LOADING_DOCUMENTS":
        case "PERSISTING_DOCUMENTS":
            return { step: "syncing", note: syncJob.phaseMessage || "Downloading and saving document metadata..." };
        case "COMPLETED":
            // Session is definitely active now
            state.session.active = true;
            elements.connectButton.innerText = "Fast Sync";
            elements.connectButton.disabled = false;
            return { step: "documents-ready", note: `Sync completed. ${syncJob.documentsPersisted ?? 0} documents synchronized.` };
        case "FAILED":
            return { step: "syncing", note: syncJob.errorMessage || "Sync failed." };
        default:
            return { step: "syncing", note: syncJob.phaseMessage || "In progress..." };
    }
}

async function stopSync() {
    if (!state.syncJob) {
        // We are likely in AUTH phase
        setBanner("Stopping Playwright automation...", "warn");
        try {
            await apiFetch("/api/v1/auth/cancel", { method: "POST" });
            setBanner("Automation stopped by user.", "warn");
            stopSyncPolling();
            elements.connectButton.disabled = false;
            toggleStopButtons(false);
            setStep("ready", "Automation cancelled.", "neutral");
            setConnectionStatus("Disconnected", "muted");
        } catch (error) {
            setBanner("Failed to stop automation: " + error.message, "error");
        }
        return;
    }

    setBanner("Stopping synchronization...", "warn");
    elements.stopSyncButton.disabled = true;
    if (elements.stopSyncButtonAlt) elements.stopSyncButtonAlt.disabled = true;

    try {
        await apiFetch(`/api/v1/sync/jobs/${state.syncJob.id}/stop`, { method: "POST" });
        // The polling loop will handle the status update
    } catch (error) {
        setBanner("Failed to stop: " + error.message, "error");
        elements.stopSyncButton.disabled = false;
        if (elements.stopSyncButtonAlt) elements.stopSyncButtonAlt.disabled = false;
    }
}

/**
 * DOCUMENT ACTIONS
 */
async function loadDocuments(page = 0) {
    const filters = new FormData(elements.filtersForm);
    const params = new URLSearchParams();

    for (const [key, value] of filters.entries()) {
        if (value) params.set(key, value);
    }

    params.set("page", page);
    params.set("size", state.pagination.size);

    try {
        const response = await apiFetch(`/api/v1/documents?${params.toString()}`);
        const payload = await response.json();

        state.documents = payload.items || [];
        state.pagination.total = payload.totalElements || 0;
        state.pagination.page = payload.pageNumber || 0;
        state.pagination.totalPages = payload.totalPages || 0;

        renderDocuments();
        updatePaginationUI();
    } catch (error) {
        setBanner("Failed to load documents: " + error.message, "error");
    }
}

function updatePaginationUI() {
    const { page, size, total, totalPages } = state.pagination;
    const start = total === 0 ? 0 : page * size + 1;
    const end = Math.min((page + 1) * size, total);

    elements.paginationRange.textContent = `${start}-${end}`;
    elements.paginationTotal.textContent = total;
    elements.currentPageLabel.textContent = `Page ${page + 1} of ${totalPages || 1}`;

    elements.prevPageButton.disabled = page <= 0;
    elements.nextPageButton.disabled = page >= totalPages - 1;
}

async function loadDocumentPreview(documentId) {
    state.activeDocumentId = documentId;
    renderDocuments();
    elements.documentPreview.innerHTML = "Fetching full document payload...";
    try {
        const response = await apiFetch(`/api/v1/documents/${documentId}`);
        const document = await response.json();
        const lineItems = (document.lines || []).map(line => `
            <div class="line-item">
                <strong>${escapeHtml(line.productName || `Line ${line.lineNumber}`)}</strong>
                <div class="muted">Code: ${escapeHtml(line.productCode)} | Qty: ${line.quantity} | Total: ${line.lineAmount}</div>
            </div>
        `).join("");

        elements.documentPreview.innerHTML = `
            <div class="preview-grid">
                <div class="field"><span class="field-label">Number</span><strong>${escapeHtml(document.documentNumber)}</strong></div>
                <div class="field"><span class="field-label">Status</span><strong>${escapeHtml(document.portalStatus)}</strong></div>
                <div class="field"><span class="field-label">Seller</span><strong>${escapeHtml(document.sellerName)}</strong></div>
                <div class="field"><span class="field-label">Buyer</span><strong>${escapeHtml(document.buyerName)}</strong></div>
            </div>
            <div class="line-items">${lineItems || "No detail items available."}</div>
        `;
    } catch (error) {
        elements.documentPreview.innerHTML = `<div class="status-banner error">${escapeHtml(error.message)}</div>`;
    }
}

/**
 * EXPORT ACTIONS
 */
async function createExport() {
    if (!state.selectedIds.size) return;
    const format = elements.exportFormat.value;
    setBanner(`Starting ${format} export...`, "neutral");
    try {
        const response = await apiFetch("/api/v1/exports", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                format: format,
                documentIds: Array.from(state.selectedIds),
                columns: format === "XLSX" ? state.exportColumns : null
            })
        });
        state.exportJob = await response.json();

        // If still running, poll (though usually instant for small sets)
        if (state.exportJob.status === "RUNNING") {
            pollExportJob(state.exportJob.id);
        } else {
            renderExportResult();
            setBanner(`${format} export ready.`, "success");
        }
    } catch (error) {
        setBanner(error.message, "error");
    }
}

async function pollExportJob(jobId) {
    try {
        const response = await apiFetch(`/api/v1/exports/jobs/${jobId}`);
        state.exportJob = await response.json();
        renderExportResult();

        if (state.exportJob.status === "RUNNING") {
            setTimeout(() => pollExportJob(jobId), 1000);
        } else if (state.exportJob.status === "COMPLETED") {
            setBanner(`${state.exportJob.outputFormat} export complete.`, "success");
        } else {
            setBanner("Export failed: " + state.exportJob.errorMessage, "error");
        }
    } catch (error) {
        setBanner("Polling failed: " + error.message, "error");
    }
}

async function downloadExportFile() {
    if (!state.exportJob?.downloadUrl) return;
    const format = state.exportJob.outputFormat.toLowerCase();
    try {
        const response = await apiFetch(state.exportJob.downloadUrl);
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `gaime-export-${new Date().toISOString().split('T')[0]}.${format}`;
        a.click();
        URL.revokeObjectURL(url);
    } catch (error) {
        setBanner("Download failed: " + error.message, "error");
    }
}

/**
 * RENDERERS
 */
function renderDocuments() {
    if (!state.documents.length) {
        elements.documentsBody.innerHTML = `<tr><td colspan="8" class="empty-state">No documents to show. Authenticate with the portal to begin.</td></tr>`;
        updateSelectionMetrics();
        return;
    }

    elements.documentsBody.innerHTML = state.documents.map(doc => {
        const checked = state.selectedIds.has(doc.id) ? "checked" : "";
        const activeClass = state.activeDocumentId === doc.id ? "active" : "";
        const directionClass = doc.direction === "OUTGOING" ? "outgoing" : "";
        return `
            <tr class="row-clickable ${activeClass}" data-document-id="${doc.id}">
                <td><input type="checkbox" class="document-checkbox" data-document-id="${doc.id}" ${checked}></td>
                <td>${escapeHtml(doc.documentDate)}</td>
                <td><strong>${escapeHtml(doc.documentNumber)}</strong></td>
                <td><span class="tag ${directionClass}">${escapeHtml(doc.direction)}</span></td>
                <td><div class="counterparty"><strong>${escapeHtml(doc.sellerName)}</strong><span>${escapeHtml(doc.sellerTaxId)}</span></div></td>
                <td><div class="counterparty"><strong>${escapeHtml(doc.buyerName)}</strong><span>${escapeHtml(doc.buyerTaxId)}</span></div></td>
                <td>${escapeHtml(doc.portalStatus)}</td>
                <td><strong>${escapeHtml(doc.totalAmount)} ${escapeHtml(doc.currencyCode)}</strong></td>
            </tr>
        `;
    }).join("");
    updateSelectionMetrics();
}

function renderExportResult() {
    if (!state.exportJob) {
        elements.exportResult.innerHTML = '<div class="empty-state">No export generated yet.</div>';
        return;
    }

    if (state.exportJob.status === "RUNNING") {
        elements.exportResult.innerHTML = `
            <div class="sync-status">
                <div class="spinner"></div>
                <span>Preparing ${state.exportJob.outputFormat}...</span>
            </div>
        `;
        return;
    }

    elements.exportResult.innerHTML = `
        <div class="field"><span class="field-label">Export Job</span><strong>${state.exportJob.id}</strong></div>
        <div class="field"><span class="field-label">Format</span><strong>${state.exportJob.outputFormat}</strong></div>
        <button id="download-button" class="accent" style="margin-top:12px">Download ${state.exportJob.outputFormat}</button>
    `;
    document.getElementById("download-button").onclick = downloadExportFile;
}

function updateSelectionMetrics() {
    elements.documentsCount.textContent = state.documents.length;
    elements.selectedCount.textContent = state.selectedIds.size;
    elements.exportButton.disabled = state.selectedIds.size === 0;
}

/**
 * HELPERS
 */
function escapeHtml(value) {
    if (!value) return "";
    return String(value).replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
}

function setupEventListeners() {
    elements.portalForm.onsubmit = async (e) => {
        e.preventDefault();

        // If we have an active session, skip auth and go straight to sync
        if (state.session.active) {
            console.log("Active session found. Triggering fast sync...");
            const formData = new FormData(elements.portalForm);
            try {
                await startPortalSync(formData);
            } catch (err) {
                // If fast sync fails (e.g. underlying session expired), clear local flag and try auth
                state.session.active = false;
                setBanner("Session seems expired. Starting fresh authentication...", "warn");
                await startAuthFlow();
            }
        } else {
            try {
                await startAuthFlow();
            } catch (err) { }
        }
    };

    elements.closeModalButton.onclick = () => {
        hideTaxpayerPicker();
        elements.connectButton.disabled = false;
        setConnectionStatus("Cancelled", "warn");
    };

    elements.filtersForm.onsubmit = (e) => {
        e.preventDefault();
        loadDocuments();
    };

    elements.refreshButton.onclick = loadDocuments;
    elements.clearFiltersButton.onclick = () => { elements.filtersForm.reset(); loadDocuments(); };

    elements.selectVisibleButton.onclick = () => {
        state.documents.forEach(d => state.selectedIds.add(d.id));
        renderDocuments();
    };

    elements.clearSelectionButton.onclick = () => {
        state.selectedIds.clear();
        renderDocuments();
    };

    elements.exportButton.onclick = createExport;

    elements.logoutButton.onclick = handleLogout;

    elements.stopSyncButton.onclick = stopSync;
    if (elements.stopSyncButtonAlt) elements.stopSyncButtonAlt.onclick = stopSync;
    
    // Custom Confirm Modal Logic
    elements.clearHistoryButton.onclick = () => elements.confirmModal.classList.remove("hidden");
    elements.executeConfirmButton.onclick = executeClearHistory;
    elements.cancelConfirmButton.onclick = () => elements.confirmModal.classList.add("hidden");

    elements.documentsBody.onclick = (e) => {
        const row = e.target.closest("tr[data-document-id]");
        if (row && !e.target.classList.contains("document-checkbox")) {
            loadDocumentPreview(row.dataset.documentId);
        }
    };

    elements.documentsBody.onchange = (e) => {
        if (e.target.classList.contains("document-checkbox")) {
            const id = e.target.dataset.documentId;
            e.target.checked ? state.selectedIds.add(id) : state.selectedIds.delete(id);
            updateSelectionMetrics();
        }
    };

    elements.exportSettingsButton.onclick = () => {
        elements.columnModal.classList.remove("hidden");
    };

    elements.saveColumnsButton.onclick = saveColumnPreferences;
    elements.resetColumnsButton.onclick = resetColumnPreferences;

    elements.prevPageButton.onclick = () => {
        if (state.pagination.page > 0) {
            loadDocuments(state.pagination.page - 1);
        }
    };

    elements.nextPageButton.onclick = () => {
        if (state.pagination.page < state.pagination.totalPages - 1) {
            loadDocuments(state.pagination.page + 1);
        }
    };

    // Close modal on outside click
    window.onclick = (e) => {
        if (e.target === elements.columnModal) elements.columnModal.classList.add("hidden");
        if (e.target === elements.taxpayerModal) hideTaxpayerPicker();
    };
}

async function executeClearHistory() {
    elements.confirmModal.classList.add("hidden");
    setBanner("Clearing database...", "warn");
    try {
        const response = await apiFetch("/api/v1/sync/clear", { method: "DELETE" });
        const data = await response.json();
        
        setBanner(data.message, "success");
        state.documents = [];
        renderDocuments();
    } catch (error) {
        setBanner("Failed to clear data: " + error.message, "error");
    }
}

// Global bootstrap
initialize();
