-- Track who was proposing BEFORE the current proposer took over.
-- Needed so that WITHDRAW (proposer cancels own proposal) can restore the
-- full prior state — including the fact that the *other* user still has
-- a pending proposal. Without this, withdraw of a counter-proposal made
-- the match look "agreed" even though the original proposal should have
-- been re-surfaced to the original proposer for response.
ALTER TABLE matches
    ADD COLUMN previous_details_proposed_by UUID;
