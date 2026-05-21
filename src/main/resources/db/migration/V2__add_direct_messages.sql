CREATE TABLE direct_messages (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id    BIGINT    NOT NULL,
    recipient_id BIGINT    NOT NULL,
    content      TEXT      NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id)    REFERENCES users(id),
    FOREIGN KEY (recipient_id) REFERENCES users(id)
);

-- Composite indexes for fast conversation lookup in both directions
CREATE INDEX idx_dm_sender_recipient ON direct_messages(sender_id, recipient_id, created_at DESC);
CREATE INDEX idx_dm_recipient_sender ON direct_messages(recipient_id, sender_id, created_at DESC);