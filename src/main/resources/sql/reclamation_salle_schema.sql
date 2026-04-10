CREATE TABLE IF NOT EXISTS reclamation_salle (
    idReclamation INT AUTO_INCREMENT PRIMARY KEY,
    idSalle INT NOT NULL,
    nomDeclarant VARCHAR(150) NOT NULL,
    referenceSeance VARCHAR(150) NULL,
    typeProbleme VARCHAR(100) NOT NULL,
    priorite VARCHAR(50) NOT NULL,
    statut VARCHAR(80) NOT NULL DEFAULT 'nouvelle',
    description TEXT NOT NULL,
    commentaireAdmin TEXT NULL,
    dateReclamation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dateTraitement DATETIME NULL,
    CONSTRAINT fk_reclamation_salle_salle
        FOREIGN KEY (idSalle) REFERENCES salle(idSalle)
        ON DELETE CASCADE
);
