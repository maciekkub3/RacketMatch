-- Coach service catalog
CREATE TABLE coach_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID NOT NULL REFERENCES coach_profiles(user_id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    pricing_type VARCHAR(20) NOT NULL,
    price_cents INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Coach personal calendar
CREATE TABLE coach_calendar_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200),
    notes TEXT,
    event_type VARCHAR(20) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    booking_id UUID REFERENCES bookings(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coach_services_coach ON coach_services(coach_id);
CREATE INDEX idx_calendar_coach ON coach_calendar_events(coach_id);
CREATE INDEX idx_calendar_range ON coach_calendar_events(coach_id, starts_at, ends_at);

-- Extend bookings
ALTER TABLE bookings ADD COLUMN service_id UUID REFERENCES coach_services(id);
ALTER TABLE bookings ADD COLUMN duration_minutes INT;

-- Remove hourly_rate from coach_profiles (price now comes from services)
ALTER TABLE coach_profiles DROP COLUMN IF EXISTS hourly_rate;
