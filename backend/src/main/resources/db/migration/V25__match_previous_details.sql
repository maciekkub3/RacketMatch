-- Snapshot of the last-agreed details so clients can render a diff when the
-- other party proposes a change (propose saves old→previous_* + overwrites,
-- accept clears previous_*, discard copies previous_* back and clears).
ALTER TABLE matches
    ADD COLUMN previous_location_name VARCHAR(255),
    ADD COLUMN previous_scheduled_at TIMESTAMPTZ;
