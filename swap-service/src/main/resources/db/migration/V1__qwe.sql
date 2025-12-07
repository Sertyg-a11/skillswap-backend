-- SWAP-SERVICE INITIAL SCHEMA

CREATE TYPE swap_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'COMPLETED');

CREATE TABLE IF NOT EXISTS swap_requests (
                                             id                  UUID PRIMARY KEY,
                                             requester_user_id   UUID NOT NULL,
                                             target_user_id      UUID NOT NULL,
                                             offered_skill_name  VARCHAR(100) NOT NULL,
    requested_skill_name VARCHAR(100) NOT NULL,
    status              swap_status NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_swap_requests_requester
    ON swap_requests(requester_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_swap_requests_target
    ON swap_requests(target_user_id, created_at DESC);
