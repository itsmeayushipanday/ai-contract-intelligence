-- owner_id has no foreign key yet: the users table arrives in Phase 11.
CREATE TABLE contracts (
    id                UUID         PRIMARY KEY,
    owner_id          UUID         NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL CHECK (file_size_bytes >= 0),
    storage_path      VARCHAR(500),
    status            VARCHAR(20)  NOT NULL
                      CHECK (status IN ('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    page_count        INTEGER      CHECK (page_count IS NULL OR page_count >= 0),
    failure_reason    VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);

-- Almost every query is "contracts belonging to this user".
CREATE INDEX idx_contracts_owner_id ON contracts (owner_id);
