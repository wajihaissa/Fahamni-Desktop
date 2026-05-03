ALTER TABLE reservation
    ADD COLUMN student_rating TINYINT NULL;

ALTER TABLE reservation
    ADD COLUMN student_review TEXT NULL;

ALTER TABLE reservation
    ADD COLUMN rated_at DATETIME NULL;
