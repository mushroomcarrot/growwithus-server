-- 운영 validate 환경에서 적용한다. 기존 성장 자료는 보존한다.
ALTER TABLE garden_growth_state ADD COLUMN IF NOT EXISTS demo_days INT NULL;

-- 기존 완료 기록과 업로드를 삭제하지 않고 새 시연 작업을 만들기 위한 보관 번호.
ALTER TABLE care_task ADD COLUMN IF NOT EXISTS archive_number BIGINT NOT NULL DEFAULT 0;
ALTER TABLE care_task DROP INDEX IF EXISTS uk_task_garden_date_type;
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_garden_date_type_archive
    ON care_task (garden_id, task_date, task_type, archive_number);
