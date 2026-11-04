-- ============================================================================
-- 2026-10-22  Prompt M — Terminal state is write-once
--
-- Enforces that once a call row enters a terminal status
-- ('ended', 'declined', 'missed', 'no_answer'), no subsequent update may
-- alter its status, ended_by, ended_at, or duration_sec.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.calls_prevent_terminal_overwrite()
RETURNS trigger AS $$
BEGIN
    -- If the row was already in a terminal status, protect terminal columns from being overwritten.
    IF OLD.status IN ('ended', 'declined', 'missed', 'no_answer') THEN
        NEW.status := OLD.status;
        NEW.ended_by := OLD.ended_by;
        NEW.ended_at := OLD.ended_at;
        NEW.duration_sec := OLD.duration_sec;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_calls_prevent_terminal_overwrite ON public.calls;

CREATE TRIGGER trg_calls_prevent_terminal_overwrite
    BEFORE UPDATE ON public.calls
    FOR EACH ROW
    EXECUTE FUNCTION public.calls_prevent_terminal_overwrite();
