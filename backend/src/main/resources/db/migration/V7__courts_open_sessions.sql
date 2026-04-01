CREATE TABLE courts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address TEXT,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    sports TEXT NOT NULL DEFAULT '',
    playtomic_url TEXT
);

CREATE TABLE open_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id UUID NOT NULL REFERENCES courts(id),
    user_id UUID NOT NULL REFERENCES users(id),
    starts_at TIMESTAMPTZ NOT NULL,
    sport VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    match_id UUID REFERENCES matches(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_courts_city ON courts(city);
CREATE INDEX idx_open_sessions_court ON open_sessions(court_id);
CREATE INDEX idx_open_sessions_user ON open_sessions(user_id);
CREATE INDEX idx_open_sessions_status ON open_sessions(status);
