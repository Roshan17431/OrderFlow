ALTER TABLE outbox_event ADD COLUMN event_sequence BIGSERIAL NOT NULL;
CREATE UNIQUE INDEX uq_outbox_event_sequence ON outbox_event(event_sequence);
CREATE INDEX idx_outbox_aggregate_sequence ON outbox_event(topic, message_key, event_sequence);
