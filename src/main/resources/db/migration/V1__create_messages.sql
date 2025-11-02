create table if not exists messages (
                                        id bigserial primary key,
                                        user_id uuid not null,
                                        content varchar(2000) not null,
    created_at timestamptz not null default now()
    );

create index if not exists idx_messages_created_at on messages (created_at desc);
