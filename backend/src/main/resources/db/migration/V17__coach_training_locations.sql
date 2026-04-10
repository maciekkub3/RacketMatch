CREATE TABLE coach_training_locations (
    coach_id UUID NOT NULL REFERENCES coach_profiles(user_id) ON DELETE CASCADE,
    location  VARCHAR(255) NOT NULL
);
