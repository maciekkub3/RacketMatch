ALTER TABLE matches
    ADD COLUMN proposed_score_challenger INT,
    ADD COLUMN proposed_score_challenged INT,
    ADD COLUMN proposed_by UUID REFERENCES users(id);
