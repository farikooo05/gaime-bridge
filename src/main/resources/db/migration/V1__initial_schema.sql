create table tax_documents
(
    id                   uuid primary key,
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    external_document_id varchar(255),
    direction            varchar(16)  not null,
    document_series      varchar(32),
    document_number      varchar(64)  not null,
    document_date        date,
    document_type_name   varchar(512),
    entry_type_name      varchar(128),
    portal_status        varchar(128),
    seller_name          varchar(255),
    seller_tax_id        varchar(64),
    buyer_name           varchar(255),
    buyer_tax_id         varchar(64),
    base_note            varchar(2048),
    additional_note      varchar(2048),
    reason_text          varchar(1024),
    currency_code        varchar(3)   not null,
    excise_amount        numeric(19, 4),
    net_amount           numeric(19, 4),
    vat_amount           numeric(19, 4),
    taxable_amount       numeric(19, 4),
    non_taxable_amount   numeric(19, 4),
    vat_exempt_amount    numeric(19, 4),
    zero_rated_amount    numeric(19, 4),
    road_tax_amount      numeric(19, 4),
    total_amount         numeric(19, 4),
    processing_state     varchar(32)  not null,
    source_row_number    integer,
    source_hash          varchar(128),
    raw_payload          text,
    parsed_payload       text,
    html_snapshot_path   varchar(1024),
    detail_loaded_at     timestamptz,
    last_synced_at       timestamptz,
    constraint uk_tax_documents_external_document_id unique (external_document_id)
);

create table tax_document_lines
(
    id               uuid primary key,
    document_id      uuid           not null references tax_documents (id) on delete cascade,
    line_number      integer,
    product_code     varchar(128),
    product_name     varchar(1024),
    description      varchar(2048),
    gtin             varchar(64),
    unit_code        varchar(64),
    quantity         numeric(19, 4),
    unit_price       numeric(19, 8),
    line_amount      numeric(19, 4),
    vat_rate         numeric(8, 4),
    vat_amount       numeric(19, 4),
    raw_line_payload text
);

create table attachments
(
    id            uuid primary key,
    document_id   uuid           not null references tax_documents (id) on delete cascade,
    file_name     varchar(512)   not null,
    file_type     varchar(64),
    storage_path  varchar(1024)  not null,
    source_url    varchar(1024),
    downloaded_at timestamptz
);

create table tax_sessions
(
    id               uuid primary key,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    username         varchar(128) not null,
    session_status   varchar(32) not null,
    expires_at       timestamptz,
    last_activity_at timestamptz,
    auth_metadata    text,
    error_message    varchar(2048)
);

create table sync_jobs
(
    id                   uuid primary key,
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    status               varchar(32) not null,
    requested_by         varchar(128) not null,
    filters_json         text,
    documents_discovered integer,
    documents_persisted  integer,
    started_at           timestamptz,
    finished_at          timestamptz,
    error_message        varchar(2048)
);

create table export_jobs
(
    id            uuid primary key,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    status        varchar(32) not null,
    output_format varchar(16) not null,
    requested_by  varchar(128) not null,
    document_count integer,
    output_path   varchar(1024),
    filters_json  text,
    started_at    timestamptz,
    finished_at   timestamptz,
    error_message varchar(2048)
);

create table integration_logs
(
    id             uuid primary key,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    log_type       varchar(32) not null,
    source_system  varchar(128),
    operation_name varchar(128) not null,
    correlation_id varchar(128),
    status         varchar(64),
    message        varchar(1024) not null,
    details        text
);

create index idx_tax_documents_number on tax_documents (document_number);
create index idx_tax_documents_date on tax_documents (document_date);
create index idx_tax_documents_status on tax_documents (portal_status);
create index idx_tax_documents_seller_tax_id on tax_documents (seller_tax_id);
create index idx_tax_documents_buyer_tax_id on tax_documents (buyer_tax_id);
create index idx_tax_document_lines_document_id on tax_document_lines (document_id);
create index idx_attachments_document_id on attachments (document_id);
