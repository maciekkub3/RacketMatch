ALTER TABLE coach_profiles
    ADD COLUMN booking_lead_time_hours INT NOT NULL DEFAULT 24,
    ADD COLUMN booking_horizon_days    INT NOT NULL DEFAULT 30,
    ADD COLUMN buffer_minutes          INT NOT NULL DEFAULT 0;
