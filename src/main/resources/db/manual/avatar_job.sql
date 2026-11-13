-- 관리사 AI 아바타 생성 작업 (#5). prod는 ddl-auto가 validate이므로 배포 전에 먼저 적용한다.
CREATE TABLE IF NOT EXISTS caretaker_avatar_job (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL,
    avatar_url VARCHAR(1024) NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    INDEX idx_avatar_job_username (username, id)
);
