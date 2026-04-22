-- Allow multiple time windows per day for coach availability
ALTER TABLE coach_availability DROP CONSTRAINT coach_availability_coach_id_day_of_week_key;
