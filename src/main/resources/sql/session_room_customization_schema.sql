CREATE TABLE IF NOT EXISTS demande_personnalisation_salle (
    idDemande INT AUTO_INCREMENT PRIMARY KEY,
    seance_id INT NOT NULL,
    base_salle_id INT NOT NULL,
    tuteur_id INT NOT NULL,
    requested_disposition VARCHAR(60) NULL,
    requested_capacity INT NULL,
    requested_table_style VARCHAR(60) NULL,
    requested_chair_style VARCHAR(60) NULL,
    accessibility_required BOOLEAN NOT NULL DEFAULT FALSE,
    requested_config_json LONGTEXT NOT NULL,
    approved_config_json LONGTEXT NULL,
    comment_tuteur TEXT NULL,
    comment_admin TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME NULL,
    CONSTRAINT fk_demande_personnalisation_salle_seance
        FOREIGN KEY (seance_id) REFERENCES seance(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_demande_personnalisation_salle_salle
        FOREIGN KEY (base_salle_id) REFERENCES salle(idSalle)
        ON DELETE CASCADE,
    CONSTRAINT uk_demande_personnalisation_salle_seance UNIQUE (seance_id),
    INDEX idx_demande_personnalisation_salle_status_created_at (status, created_at)
);
