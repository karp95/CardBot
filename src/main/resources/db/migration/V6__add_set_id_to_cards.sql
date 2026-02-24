ALTER TABLE cards ADD COLUMN set_id BIGINT REFERENCES card_sets(id) ON DELETE SET NULL;
CREATE INDEX idx_cards_set_id ON cards(set_id);
