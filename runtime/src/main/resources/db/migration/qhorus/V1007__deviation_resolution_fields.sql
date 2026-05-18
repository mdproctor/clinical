-- Resolution fields for protocol deviation ledger entries (clinical#14).
-- COMMAND entries (written by ProtocolDeviationService) leave both columns null.
-- Resolution entries (written by PiResponseListener and DeviationExpirationJob) populate both.
ALTER TABLE protocol_deviation_ledger_entry
    ADD COLUMN terminal_status VARCHAR(50),
    ADD COLUMN resolved_at     TIMESTAMP WITH TIME ZONE;
