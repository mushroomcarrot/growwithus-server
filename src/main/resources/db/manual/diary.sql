CREATE TABLE IF NOT EXISTS diary_entry (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    garden_id BIGINT NOT NULL,
    entry_type VARCHAR(24) NOT NULL,
    source_id BIGINT NOT NULL,
    task_types VARCHAR(200) NULL,
    transcript TEXT NULL,
    summary VARCHAR(1000) NOT NULL,
    photo_url VARCHAR(1024) NULL,
    caretaker_name VARCHAR(20) NULL,
    from_stage VARCHAR(16) NULL,
    to_stage VARCHAR(16) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_diary_source UNIQUE (entry_type, source_id),
    INDEX idx_diary_garden_id (garden_id, id)
);
