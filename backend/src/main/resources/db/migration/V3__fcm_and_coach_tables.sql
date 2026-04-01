-- Add FCM token to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token TEXT;

-- Fix coach_profiles: replace TEXT[] arrays with proper join tables
-- (Hibernate @ElementCollection requires separate tables, not PostgreSQL arrays)
ALTER TABLE coach_profiles DROP COLUMN IF EXISTS certifications;
ALTER TABLE coach_profiles DROP COLUMN IF EXISTS sports;

CREATE TABLE IF NOT EXISTS coach_certifications (
    coach_id    UUID NOT NULL REFERENCES coach_profiles(user_id) ON DELETE CASCADE,
    certification TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS coach_sports (
    coach_id UUID NOT NULL REFERENCES coach_profiles(user_id) ON DELETE CASCADE,
    sport    TEXT NOT NULL
);
