-- One row per handled message, so a redelivered message is recognised by its event_id.
CREATE TABLE processed_events (
    event_id     UUID         PRIMARY KEY,
    processed_at TIMESTAMPTZ  NOT NULL
);
