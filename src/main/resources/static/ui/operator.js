const state = {
    authHeader: `Basic ${btoa("dev:dev123")}`,
    documents: [],
    selectedIds: new Set(),
    activeDocumentId: null,
    exportJob: null,
    uiStep: "credentials",
    syncJob: null,
    syncPollHandle: null
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
    dateFrom: document.getElementById("date-from"),
    dateTo: document.getElementById("date-to")
};

function initializeDefaultDates() {
    const today = new Date();
    const isoToday = today.toISOString().slice(0, 10);
    const start = new Date(today);
    start.setMonth(start.getMonth() - 1);
    const isoStart = start.toISOString().slice(0, 10);
    elements.dateFrom.value = isoStart;
    elements.dateTo.value = isoToday;
}

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

async function apiFetch(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.authHeader) {
        headers.set("Authorization", state.authHeader);
    }
    const response = await fetch(path, {
        ...options,
        headers
    });

    if (!response.ok) {
        let message = `Request failed with status ${response.status}`;
        try {
            const body = await response.json();
            if (body.message) {
                message = body.message;
            }
        } catch (_) {
        }
        throw new Error(message);
    }
    return response;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function formatMoney(amount, currencyCode) {
    if (amount == null) {
        return "n/a";
    }
    return `${amount} ${currencyCode || ""}`.trim();
}

function formatDate(value) {
    return value || "n/a";
}

function updateSelectionMetrics() {
    elements.documentsCount.textContent = state.documents.length;
    elements.selectedCount.textContent = state.selectedIds.size;
    elements.exportButton.disabled = state.selectedIds.size === 0;
}

function showDocumentsSection() {
    elements.documentsSection.classList.remove("hidden");
}

function renderDocuments() {
    if (!state.documents.length) {
        elements.documentsBody.innerHTML = `<tr><td colspan="8" class="empty-state">No documents found yet. Run a real sync to bring your portal documents here.</td></tr>`;
        updateSelectionMetrics();
        return;
    }

    elements.documentsBody.innerHTML = state.documents.map((document) => {
        const checked = state.selectedIds.has(document.id) ? "checked" : "";
        const activeClass = state.activeDocumentId === document.id ? "active" : "";
        const directionClass = document.direction === "OUTGOING" ? "outgoing" : "";
        return `
            <tr class="row-clickable ${activeClass}" data-document-id="${document.id}">
                <td><input type="checkbox" class="document-checkbox" data-document-id="${document.id}" ${checked}></td>
                <td>${escapeHtml(formatDate(document.documentDate))}</td>
                <td><strong>${escapeHtml(document.documentNumber)}</strong><br><span class="muted">${escapeHtml(document.documentSeries || "")}</span></td>
                <td><span class="tag ${directionClass}">${escapeHtml(document.direction)}</span></td>
                <td><div class="counterparty"><strong>${escapeHtml(document.sellerName)}</strong><span>${escapeHtml(document.sellerTaxId || "")}</span></div></td>
                <td><div class="counterparty"><strong>${escapeHtml(document.buyerName)}</strong><span>${escapeHtml(document.buyerTaxId || "")}</span></div></td>
                <td>${escapeHtml(document.portalStatus || "n/a")}<br><span class="muted">${escapeHtml(document.processingState || "")}</span></td>
                <td><strong>${escapeHtml(formatMoney(document.totalAmount, document.currencyCode))}</strong><br><span class="muted">VAT ${escapeHtml(document.vatAmount ?? "n/a")}</span></td>
            </tr>
        `;
    }).join("");

    updateSelectionMetrics();
}

async function loadDocuments() {
    const params = new URLSearchParams();
    const formData = new FormData(elements.filtersForm);
    for (const [key, value] of formData.entries()) {
        if (value) {
            params.set(key, value);
        }
    }

    const response = await apiFetch(`/api/v1/documents?${params.toString()}`);
    const payload = await response.json();
    state.documents = payload.items || [];
    state.selectedIds.forEach((id) => {
        if (!state.documents.some((document) => document.id === id)) {
            state.selectedIds.delete(id);
        }
    });
    renderDocuments();
}

async function loadDocumentPreview(documentId) {
    state.activeDocumentId = documentId;
    renderDocuments();
    elements.documentPreview.innerHTML = "Loading document details...";
    try {
        const response = await apiFetch(`/api/v1/documents/${documentId}`);
        const document = await response.json();
        const lineItems = (document.lines || []).map((line) => `
            <div class="line-item">
                <strong>${escapeHtml(line.productName || `Line ${line.lineNumber || ""}`)}</strong>
                <div class="muted">Code: ${escapeHtml(line.productCode || "n/a")} | Unit: ${escapeHtml(line.unitCode || "n/a")}</div>
                <div>Qty: ${escapeHtml(line.quantity ?? "n/a")} | Price: ${escapeHtml(line.unitPrice ?? "n/a")} | Total: ${escapeHtml(line.lineAmount ?? "n/a")}</div>
            </div>
        `).join("");

        elements.documentPreview.innerHTML = `
            <div class="preview-grid">
                <div class="field"><span class="field-label">Document</span><strong>${escapeHtml(document.documentSeries || "")} ${escapeHtml(document.documentNumber)}</strong></div>
                <div class="field"><span class="field-label">Date</span><strong>${escapeHtml(formatDate(document.documentDate))}</strong></div>
                <div class="field"><span class="field-label">Direction</span><strong>${escapeHtml(document.direction)}</strong></div>
                <div class="field"><span class="field-label">Status</span><strong>${escapeHtml(document.portalStatus || "n/a")}</strong></div>
                <div class="field"><span class="field-label">Seller</span><strong>${escapeHtml(document.sellerName)}</strong><div class="muted">${escapeHtml(document.sellerTaxId || "")}</div></div>
                <div class="field"><span class="field-label">Buyer</span><strong>${escapeHtml(document.buyerName)}</strong><div class="muted">${escapeHtml(document.buyerTaxId || "")}</div></div>
                <div class="field"><span class="field-label">Total</span><strong>${escapeHtml(formatMoney(document.totalAmount, document.currencyCode))}</strong></div>
                <div class="field"><span class="field-label">Reason</span><strong>${escapeHtml(document.reasonText || document.baseNote || "n/a")}</strong></div>
            </div>
            <div class="line-items">
                ${lineItems || '<div class="empty-state">No line items available.</div>'}
            </div>
        `;
    } catch (error) {
        elements.documentPreview.innerHTML = `<div class="empty-state">${escapeHtml(error.message)}</div>`;
    }
}

function renderExportResult() {
    if (!state.exportJob) {
        elements.exportResult.innerHTML = "No export started yet.";
        return;
    }

    const downloadAction = state.exportJob.downloadUrl
        ? `<div class="inline-actions"><button id="download-export-button" type="button">Download JSON</button></div>`
        : "";

    elements.exportResult.innerHTML = `
        <div class="preview-grid">
            <div class="field"><span class="field-label">Job ID</span><strong>${escapeHtml(state.exportJob.id)}</strong></div>
            <div class="field"><span class="field-label">Status</span><strong>${escapeHtml(state.exportJob.status)}</strong></div>
            <div class="field"><span class="field-label">Documents</span><strong>${escapeHtml(state.exportJob.documentCount ?? "0")}</strong></div>
            <div class="field"><span class="field-label">Format</span><strong>${escapeHtml(state.exportJob.outputFormat || "JSON")}</strong></div>
        </div>
        ${state.exportJob.errorMessage ? `<div class="line-items"><div class="line-item">${escapeHtml(state.exportJob.errorMessage)}</div></div>` : ""}
        ${downloadAction}
    `;

    const downloadButton = document.getElementById("download-export-button");
    if (downloadButton) {
        downloadButton.addEventListener("click", downloadExportFile);
    }
}

async function downloadExportFile() {
    if (!state.exportJob?.downloadUrl) {
        setBanner("No export file is available yet.", "warning");
        return;
    }

    try {
        const response = await apiFetch(state.exportJob.downloadUrl);
        const blob = await response.blob();
        const blobUrl = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = blobUrl;
        link.download = `export-${state.exportJob.id}.json`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(blobUrl);
        setBanner("Export file downloaded.", "success");
    } catch (error) {
        setBanner(error.message, "error");
    }
}

async function createExport() {
    if (!state.selectedIds.size) {
        setBanner("Select at least one document before exporting.", "warning");
        return;
    }

    elements.exportButton.disabled = true;
    setBanner("Preparing JSON export...", "neutral");

    try {
        const response = await apiFetch("/api/v1/exports", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                format: "JSON",
                documentIds: Array.from(state.selectedIds)
            })
        });
        state.exportJob = await response.json();
        renderExportResult();
        setBanner(`Export ${state.exportJob.status.toLowerCase()}.`, state.exportJob.status === "COMPLETED" ? "success" : "warning");
    } catch (error) {
        setBanner(error.message, "error");
    } finally {
        updateSelectionMetrics();
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
        case "WAITING_FOR_PHONE_CONFIRMATION":
            return {
                step: "phone-confirmation",
                note: syncJob.phaseMessage || "Confirm the Asan request on your phone."
            };
        case "WAITING_FOR_COMPANY_SELECTION":
            return {
                step: "company-selection",
                note: syncJob.phaseMessage || "Choose your company in the opened portal window."
            };
        case "LOADING_DOCUMENTS":
        case "PERSISTING_DOCUMENTS":
            return {
                step: "syncing",
                note: syncJob.phaseMessage || "Dynorix is loading and saving your real portal documents."
            };
        case "COMPLETED":
            return {
                step: "documents-ready",
                note: syncJob.phaseMessage || `Sync completed. ${syncJob.documentsPersisted ?? 0} documents were saved.`
            };
        case "FAILED":
            return {
                step: state.uiStep === "documents-ready" ? "documents-ready" : "syncing",
                note: syncJob.errorMessage || syncJob.phaseMessage || "Sync failed."
            };
        case "QUEUED":
        case "OPENING_LOGIN":
        default:
            return {
                step: "credentials",
                note: syncJob.phaseMessage || "Opening the portal login page."
            };
    }
}

async function refreshSyncJob(jobId) {
    const response = await apiFetch(`/api/v1/sync/jobs/${jobId}`);
    return response.json();
}

async function pollSyncJob(jobId) {
    try {
        const syncJob = await refreshSyncJob(jobId);
        state.syncJob = syncJob;
        const mapped = mapPhaseToStep(syncJob);
        setStep(mapped.step, mapped.note, syncJob.status === "FAILED" ? "error" : syncJob.status === "COMPLETED" ? "success" : "neutral");

        if (syncJob.status === "COMPLETED") {
            stopSyncPolling();
            showDocumentsSection();
            await loadDocuments();
            return;
        }

        if (syncJob.status === "FAILED") {
            stopSyncPolling();
            throw new Error(syncJob.errorMessage || syncJob.phaseMessage || "Sync failed.");
        }

        state.syncPollHandle = window.setTimeout(() => {
            pollSyncJob(jobId).catch((error) => {
                stopSyncPolling();
                setBanner(error.message, "error");
                elements.journeyNote.textContent = error.message;
                elements.connectButton.disabled = false;
            });
        }, 1500);
    } catch (error) {
        stopSyncPolling();
        throw error;
    }
}

async function startPortalSync(formData) {
    state.exportJob = null;
    stopSyncPolling();
    renderExportResult();
    state.selectedIds.clear();
    state.activeDocumentId = null;
    elements.documentPreview.innerHTML = "No document selected.";

    setStep("credentials", "Starting a secure portal session for your real documents.", "neutral");

    const payload = {
        portalPhone: formData.get("portalPhone"),
        portalUserId: formData.get("portalUserId"),
        dateFrom: formData.get("dateFrom"),
        dateTo: formData.get("dateTo"),
        loadDocumentDetails: formData.get("loadDocumentDetails") === "on"
    };

    const response = await apiFetch("/api/v1/sync/documents", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
    });
    state.syncJob = await response.json();
    const mapped = mapPhaseToStep(state.syncJob);
    setStep(mapped.step, mapped.note, "neutral");
    await pollSyncJob(state.syncJob.id);
}

elements.portalForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    elements.connectButton.disabled = true;
    const formData = new FormData(elements.portalForm);

    try {
        await startPortalSync(formData);
    } catch (error) {
        setBanner(error.message, "error");
        elements.journeyNote.textContent = error.message;
    } finally {
        if (!state.syncJob || state.syncJob.status === "COMPLETED" || state.syncJob.status === "FAILED") {
            elements.connectButton.disabled = false;
        }
    }
});

elements.filtersForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        await loadDocuments();
        setBanner(`Loaded ${state.documents.length} documents.`, "success");
    } catch (error) {
        setBanner(error.message, "error");
    }
});

elements.refreshButton.addEventListener("click", async () => {
    try {
        await loadDocuments();
        setBanner(`Loaded ${state.documents.length} documents.`, "success");
    } catch (error) {
        setBanner(error.message, "error");
    }
});

elements.clearFiltersButton.addEventListener("click", () => {
    elements.filtersForm.reset();
});

elements.selectVisibleButton.addEventListener("click", () => {
    state.documents.forEach((document) => state.selectedIds.add(document.id));
    renderDocuments();
});

elements.clearSelectionButton.addEventListener("click", () => {
    state.selectedIds.clear();
    renderDocuments();
});

elements.exportButton.addEventListener("click", async () => {
    await createExport();
});

elements.documentsBody.addEventListener("change", (event) => {
    if (!event.target.classList.contains("document-checkbox")) {
        return;
    }
    const documentId = event.target.dataset.documentId;
    if (event.target.checked) {
        state.selectedIds.add(documentId);
    } else {
        state.selectedIds.delete(documentId);
    }
    updateSelectionMetrics();
});

elements.documentsBody.addEventListener("click", async (event) => {
    const checkbox = event.target.closest(".document-checkbox");
    if (checkbox) {
        return;
    }
    const row = event.target.closest("tr[data-document-id]");
    if (!row) {
        return;
    }
    await loadDocumentPreview(row.dataset.documentId);
});

initializeDefaultDates();
renderDocuments();
renderExportResult();
updateSelectionMetrics();
