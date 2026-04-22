-- Per-sport skill level and ELO. Replaces the single global elo_rating as
-- the authoritative source, but keeps users.elo_rating around for now as
-- a compatibility shim (writes go to both during transition).
--
-- seed_tier: 1..6 (Nowicjusz..Pro), ELO seed = 400 + tier*200
-- calibration_matches: 0..10 — while < 10, K-factor is doubled
CREATE TABLE user_sport_level (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sport                 VARCHAR(20) NOT NULL,
    seed_tier             INT NOT NULL,
    elo_rating            INT NOT NULL,
    calibration_matches   INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_sport UNIQUE (user_id, sport)
);

CREATE INDEX idx_user_sport_level_user ON user_sport_level(user_id);

-- Backfill: for every existing user, seed a row per declared sport. Use
-- their current elo_rating as the starting per-sport ELO and mark them
-- as already calibrated (matches = 10) so their rating doesn't suddenly
-- start swinging wildly. seed_tier = 3 (Amator) is just a fallback label
-- — the ELO is what actually matters here.
INSERT INTO user_sport_level (id, user_id, sport, seed_tier, elo_rating, calibration_matches)
SELECT
    gen_random_uuid(),
    u.id,
    trim(sport_name),
    3,
    COALESCE(u.elo_rating, 1200),
    10
FROM users u,
LATERAL unnest(string_to_array(NULLIF(u.sports, ''), ',')) AS sport_name
WHERE trim(sport_name) <> '';

-- Age confirmation flag from the Register checkbox (GDPR art. 8 / RODO
-- requires 16+ in Poland for self-consent to data processing). Existing
-- users default to TRUE — they registered before the checkbox existed,
-- so we treat them as grandfathered.
ALTER TABLE users
    ADD COLUMN age_confirmed BOOLEAN NOT NULL DEFAULT TRUE;
