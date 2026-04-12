CREATE TABLE IF NOT EXISTS maintenance_salle (
    idMaintenance INT AUTO_INCREMENT PRIMARY KEY,
    idSalle INT NOT NULL,
    idReclamation INT NULL,
    typeMaintenance VARCHAR(100) NOT NULL,
    statut VARCHAR(50) NOT NULL DEFAULT 'planifiee',
    responsable VARCHAR(150) NULL,
    detailsIntervention TEXT NULL,
    datePlanifiee DATE NULL,
    dateDebut DATE NULL,
    dateFin DATE NULL,
    dateCreation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_maintenance_salle_reclamation UNIQUE (idReclamation),
    CONSTRAINT fk_maintenance_salle_salle
        FOREIGN KEY (idSalle) REFERENCES salle(idSalle)
        ON DELETE CASCADE,
    CONSTRAINT fk_maintenance_salle_reclamation
        FOREIGN KEY (idReclamation) REFERENCES reclamation_salle(idReclamation)
        ON DELETE SET NULL
);
