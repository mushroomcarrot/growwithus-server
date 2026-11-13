-- MariaDB, 새 테이블용. 배포 전 백업 후 스테이징에서 검증한다.
-- 기존 팀 테이블과 이름이 겹치면 합의 후 적용한다. 자동 실행하지 않는다.
CREATE TABLE IF NOT EXISTS care_task_day (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    caretaker_username VARCHAR(20) NOT NULL,
    task_date DATE NOT NULL,
    CONSTRAINT uk_task_day_caretaker_date UNIQUE (caretaker_username, task_date)
);
CREATE TABLE IF NOT EXISTS care_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    garden_id BIGINT NOT NULL,
    caretaker_username VARCHAR(20) NOT NULL,
    task_date DATE NOT NULL,
    task_type VARCHAR(24) NOT NULL,
    title VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    minutes INT NOT NULL,
    source_id VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL,
    care_log_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT uk_task_garden_date_type UNIQUE (garden_id, task_date, task_type),
    INDEX idx_task_caretaker_date (caretaker_username, task_date),
    INDEX idx_task_garden_completed (garden_id, completed_at)
);
CREATE TABLE IF NOT EXISTS care_task_photo (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    caretaker_username VARCHAR(20) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    ai_matched BIT NULL,
    ai_message VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_photo_task (task_id)
);
CREATE TABLE IF NOT EXISTS care_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    garden_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL UNIQUE,
    caretaker_username VARCHAR(20) NOT NULL,
    owner_username VARCHAR(20) NOT NULL,
    photo_url VARCHAR(1024) NULL,
    voice_memo_id BIGINT NULL,
    transcript TEXT NULL,
    summary VARCHAR(1000) NOT NULL,
    hearts_received BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_log_caretaker_created (caretaker_username, created_at)
);
CREATE TABLE IF NOT EXISTS caretaker_profile (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(20) NOT NULL,
    farm_id BIGINT NOT NULL,
    avatar_url VARCHAR(1024) NOT NULL,
    portrait_consent BIT NOT NULL DEFAULT b'0',
    portrait_consented_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS garden_growth_state (
    garden_id BIGINT NOT NULL PRIMARY KEY,
    stage VARCHAR(16) NOT NULL,
    accumulated_gdd DOUBLE NOT NULL,
    progress DOUBLE NOT NULL,
    expected_harvest_date DATE NULL,
    calibration_on DATE NULL,
    calibration_stage VARCHAR(16) NULL,
    calibrated_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS growth_stage_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    garden_id BIGINT NOT NULL,
    from_stage VARCHAR(16) NOT NULL,
    to_stage VARCHAR(16) NOT NULL,
    summary VARCHAR(300) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_growth_event_garden (garden_id, id)
);
