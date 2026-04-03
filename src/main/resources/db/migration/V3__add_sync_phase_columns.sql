alter table sync_jobs add column if not exists phase varchar(64) default 'QUEUED';
alter table sync_jobs add column if not exists phase_message varchar(2048);
update sync_jobs set phase = 'QUEUED' where phase is null;
