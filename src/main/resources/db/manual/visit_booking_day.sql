CREATE TABLE IF NOT EXISTS visit_booking_day (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    visit_date DATE NOT NULL,
    CONSTRAINT uk_visit_booking_farm_date UNIQUE (farm_id, visit_date)
) ENGINE=InnoDB;
