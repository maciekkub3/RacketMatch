ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS details_proposed_by UUID REFERENCES users(id),
    ALTER COLUMN scheduled_at TYPE TIMESTAMPTZ USING scheduled_at::TIMESTAMPTZ,
    ALTER COLUMN location_name TYPE TEXT;
