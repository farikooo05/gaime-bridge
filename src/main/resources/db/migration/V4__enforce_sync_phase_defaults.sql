update sync_jobs
set phase = 'QUEUED'
where phase is null;

alter table sync_jobs
    alter column phase set default 'QUEUED';

alter table sync_jobs
    alter column phase set not null;
