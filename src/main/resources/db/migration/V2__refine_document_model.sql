alter table tax_documents
    add column if not exists portal_detail_url varchar(1024),
    add column if not exists portal_created_at timestamptz,
    add column if not exists portal_signed_at timestamptz,
    add column if not exists portal_updated_at timestamptz,
    add column if not exists object_name varchar(512),
    add column if not exists document_version_label varchar(128),
    add column if not exists amendment_type varchar(256),
    add column if not exists related_document_number varchar(128),
    add column if not exists advance_document_series varchar(32),
    add column if not exists advance_document_number varchar(64),
    add column if not exists advance_amount numeric(19, 4);

create table if not exists tax_document_snapshots
(
    document_id        uuid primary key references tax_documents (id) on delete cascade,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    source_row_number  integer,
    source_hash        varchar(128),
    raw_payload        text,
    parsed_payload     text,
    html_snapshot_path varchar(1024),
    extracted_at       timestamptz,
    parser_version     varchar(64)
);

create unique index if not exists idx_tax_document_snapshots_document_id on tax_document_snapshots (document_id);

insert into tax_document_snapshots (
    document_id,
    created_at,
    updated_at,
    source_row_number,
    source_hash,
    raw_payload,
    parsed_payload,
    html_snapshot_path,
    extracted_at,
    parser_version
)
select id,
       now(),
       now(),
       source_row_number,
       source_hash,
       raw_payload,
       parsed_payload,
       html_snapshot_path,
       coalesce(detail_loaded_at, last_synced_at),
       'legacy'
from tax_documents
where source_row_number is not null
   or source_hash is not null
   or raw_payload is not null
   or parsed_payload is not null
   or html_snapshot_path is not null
on conflict (document_id) do nothing;
