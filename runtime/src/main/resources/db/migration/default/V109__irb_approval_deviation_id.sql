-- V109: Link IrbApproval to its originating ProtocolDeviation.
-- Required for IrbDecisionListener to query IrbApproval by deviationId.
-- Nullable: existing stub rows have no linked deviation.
ALTER TABLE irb_approval
    ADD COLUMN deviation_id UUID REFERENCES protocol_deviation(id);
