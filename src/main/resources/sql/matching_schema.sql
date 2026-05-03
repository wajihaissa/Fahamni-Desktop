CREATE TABLE IF NOT EXISTS matching_request (
    id INT AUTO_INCREMENT PRIMARY KEY,
    participant_id INT NOT NULL,
    matiere VARCHAR(255) NOT NULL,
    start_at DATETIME NOT NULL,
    duration_min INT NOT NULL,
    mode_seance VARCHAR(30) NOT NULL DEFAULT 'en_ligne',
    visibility_scope VARCHAR(20) NOT NULL DEFAULT 'publique',
    objective_text TEXT NULL,
    objective_summary VARCHAR(255) NULL,
    need_keywords VARCHAR(255) NULL,
    requested_level VARCHAR(60) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    accepted_tutor_id INT NULL,
    planned_seance_id INT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    INDEX idx_matching_request_participant (participant_id),
    INDEX idx_matching_request_status (status),
    INDEX idx_matching_request_start_at (start_at)
);

CREATE TABLE IF NOT EXISTS matching_candidate (
    id INT AUTO_INCREMENT PRIMARY KEY,
    request_id INT NOT NULL,
    tutor_id INT NOT NULL,
    compatibility_score DOUBLE NOT NULL DEFAULT 0,
    match_reason VARCHAR(255) NULL,
    supporting_signals VARCHAR(255) NULL,
    student_decision SMALLINT NULL,
    tutor_decision SMALLINT NULL,
    created_at DATETIME NOT NULL,
    responded_at DATETIME NULL,
    UNIQUE KEY uq_matching_request_tutor (request_id, tutor_id),
    INDEX idx_matching_candidate_tutor (tutor_id),
    INDEX idx_matching_candidate_request (request_id)
);

CREATE TABLE IF NOT EXISTS matching_session_visibility (
    seance_id INT PRIMARY KEY,
    request_id INT NOT NULL,
    participant_id INT NOT NULL,
    visibility_scope VARCHAR(20) NOT NULL DEFAULT 'publique',
    created_at DATETIME NOT NULL,
    INDEX idx_matching_visibility_participant (participant_id)
);
