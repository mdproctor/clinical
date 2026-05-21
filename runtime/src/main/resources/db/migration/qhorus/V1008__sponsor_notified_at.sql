-- sponsor_notified_at column for protocol deviation ledger entries (clinical#13).
-- When a sponsor notification is delivered successfully, this field is set to the delivery timestamp.
-- Failed notifications leave this field null, but an entry is still written with actorRole 'sponsor-notifier-failed'.
ALTER TABLE protocol_deviation_ledger_entry
    ADD COLUMN sponsor_notified_at TIMESTAMP WITH TIME ZONE;
