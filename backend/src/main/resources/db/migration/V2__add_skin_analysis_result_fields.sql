ALTER TABLE skin_concern ADD COLUMN ui_score INTEGER;
ALTER TABLE skin_concern ADD COLUMN raw_score NUMERIC(12, 9);

CREATE TABLE skin_concern_mask (
    id UUID PRIMARY KEY,
    skin_concern_id UUID NOT NULL,
    mask_url VARCHAR(2048) NOT NULL,
    CONSTRAINT fk_skin_concern_mask_concern
        FOREIGN KEY (skin_concern_id) REFERENCES skin_concern (id)
);
