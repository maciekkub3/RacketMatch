ALTER TABLE bookings
    ADD COLUMN decline_reason TEXT,
    ADD COLUMN cancel_reason TEXT,
    ADD COLUMN late_cancel BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN player_note TEXT,
    ADD COLUMN conversation_id TEXT,
    ADD COLUMN previous_booking_id UUID REFERENCES bookings(id),
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE bookings SET updated_at = created_at WHERE updated_at IS NULL;

CREATE INDEX idx_bookings_coach_status ON bookings(coach_id, status);
CREATE INDEX idx_bookings_player_status ON bookings(player_id, status);
CREATE INDEX idx_bookings_reminder ON bookings(status, starts_at, reminder_sent)
    WHERE status = 'CONFIRMED' AND reminder_sent = FALSE;
