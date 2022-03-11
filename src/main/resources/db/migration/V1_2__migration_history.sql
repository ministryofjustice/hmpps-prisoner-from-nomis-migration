create table migration_history
(
    migration_id           varchar(40)                            not null
        constraint migration_history_migration_id_pkey primary key,
    when_started           timestamp with time zone default now() not null,
    when_ended             timestamp with time zone,
    estimated_record_count bigint                                 not null,
    records_migrated       bigint                   default 0,
    records_failed         bigint                   default 0,
    migration_type         varchar(20)                            not null,
    status                 varchar(20)                            not null,
    filter                 text
);

