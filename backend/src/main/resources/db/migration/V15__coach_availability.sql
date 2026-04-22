CREATE TABLE coach_availability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    UNIQUE (coach_id, day_of_week)
);

CREATE INDEX idx_availability_coach ON coach_availability(coach_id);
