ALTER TABLE question
    ADD COLUMN IF NOT EXISTS topic VARCHAR(190) NULL AFTER question,
    ADD COLUMN IF NOT EXISTS difficulty VARCHAR(40) NOT NULL DEFAULT 'Medium' AFTER topic,
    ADD COLUMN IF NOT EXISTS source_question_id BIGINT NULL AFTER difficulty,
    ADD COLUMN IF NOT EXISTS hint TEXT NULL AFTER source_question_id,
    ADD COLUMN IF NOT EXISTS explanation TEXT NULL AFTER hint,
    ADD COLUMN IF NOT EXISTS question_type VARCHAR(40) NOT NULL DEFAULT 'multiple_choice' AFTER explanation,
    ADD COLUMN IF NOT EXISTS code_language VARCHAR(80) NULL AFTER question_type,
    ADD COLUMN IF NOT EXISTS starter_code TEXT NULL AFTER code_language,
    ADD COLUMN IF NOT EXISTS expected_answer TEXT NULL AFTER starter_code,
    ADD COLUMN IF NOT EXISTS code_evaluation_mode VARCHAR(20) NOT NULL DEFAULT 'strict' AFTER expected_answer;

ALTER TABLE quiz_result
    ADD COLUMN IF NOT EXISTS total_questions INT NULL AFTER score,
    ADD COLUMN IF NOT EXISTS percentage DOUBLE NULL AFTER total_questions,
    ADD COLUMN IF NOT EXISTS passed BOOLEAN NULL AFTER percentage,
    ADD COLUMN IF NOT EXISTS user_id INT NULL AFTER passed,
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(190) NULL AFTER user_id,
    ADD COLUMN IF NOT EXISTS user_full_name VARCHAR(190) NULL AFTER user_email;

CREATE TABLE IF NOT EXISTS quiz_answer_attempt (
    id INT AUTO_INCREMENT PRIMARY KEY,
    quiz_result_id INT NOT NULL,
    question_id INT NOT NULL,
    selected_choice_id INT NULL,
    submitted_answer TEXT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    answered_at DATETIME NOT NULL,
    CONSTRAINT fk_quiz_answer_attempt_result
        FOREIGN KEY (quiz_result_id) REFERENCES quiz_result(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_quiz_answer_attempt_question
        FOREIGN KEY (question_id) REFERENCES question(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_quiz_answer_attempt_choice
        FOREIGN KEY (selected_choice_id) REFERENCES choice(id)
        ON DELETE SET NULL
);
