CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenger_id UUID REFERENCES users(id),
    challenged_id UUID REFERENCES users(id),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    sport VARCHAR(20) NOT NULL DEFAULT 'TENNIS',
    scheduled_at TIMESTAMPTZ,
    location_name TEXT,
    score_challenger INT,
    score_challenged INT,
    elo_change_challenger INT,
    elo_change_challenged INT,
    payment_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID REFERENCES matches(id),
    sender_id UUID REFERENCES users(id),
    text TEXT NOT NULL,
    sent_at TIMESTAMPTZ DEFAULT NOW(),
    is_read BOOLEAN DEFAULT FALSE
);

CREATE TABLE coach_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    bio TEXT,
    hourly_rate INT NOT NULL,
    certifications TEXT[],
    sports TEXT[] DEFAULT '{TENNIS}'
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID REFERENCES users(id),
    player_id UUID REFERENCES users(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_matches_challenger ON matches(challenger_id);
CREATE INDEX idx_matches_challenged ON matches(challenged_id);
CREATE INDEX idx_chat_match ON chat_messages(match_id);
CREATE INDEX idx_bookings_coach ON bookings(coach_id);
