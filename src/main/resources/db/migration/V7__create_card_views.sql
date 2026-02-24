CREATE TABLE card_views (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id     BIGINT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    viewed_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_card_views_user_id ON card_views(user_id);
CREATE INDEX idx_card_views_card_id ON card_views(card_id);
CREATE INDEX idx_card_views_viewed_at ON card_views(viewed_at);
CREATE INDEX idx_card_views_user_viewed ON card_views(user_id, viewed_at);
