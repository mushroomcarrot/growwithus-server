-- MariaDB 수동 적용용. 자동 실행되지 않으며 기존 테이블이 있으면 실패한다.
CREATE TABLE voice_memo (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    caretaker_username VARCHAR(20) NOT NULL,
    audio_url VARCHAR(1024) NOT NULL,
    transcript TEXT,
    summary VARCHAR(1000),
    issue_codes VARCHAR(200) NOT NULL,
    fallback_used BIT NOT NULL,
    status VARCHAR(16) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processing_token VARCHAR(36),
    processing_started_at DATETIME(6),
    processing_error VARCHAR(16),
    created_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_voice_task_confirmed (task_id, status, confirmed_at)
);
