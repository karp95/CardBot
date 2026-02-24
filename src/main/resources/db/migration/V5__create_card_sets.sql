CREATE TABLE card_sets (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_card_sets_user_id ON card_sets(user_id);
CREATE UNIQUE INDEX idx_card_sets_user_name ON card_sets(user_id, LOWER(name));
