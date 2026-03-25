CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    is_coach BOOLEAN DEFAULT FALSE,
    city VARCHAR(100) NOT NULL,
    location GEOGRAPHY(Point, 4326),
    elo_rating INT DEFAULT 1200,
    is_master BOOLEAN DEFAULT FALSE,
    master_fee INT,
    subscription_active BOOLEAN DEFAULT FALSE,
    matches_played INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_users_location ON users USING GIST(location);
CREATE INDEX idx_users_city ON users(city);
CREATE INDEX idx_users_elo ON users(elo_rating DESC);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
