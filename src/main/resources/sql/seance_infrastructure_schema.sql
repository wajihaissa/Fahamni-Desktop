ALTER TABLE seance
    ADD COLUMN mode_seance VARCHAR(30) NOT NULL DEFAULT 'en_ligne';

ALTER TABLE seance
    ADD COLUMN salle_id INT NULL;

CREATE TABLE IF NOT EXISTS seance_equipement (
    seance_id INT NOT NULL,
    idEquipement INT NOT NULL,
    quantite INT NOT NULL DEFAULT 1,
    PRIMARY KEY (seance_id, idEquipement),
    CONSTRAINT fk_seance_equipement_seance
        FOREIGN KEY (seance_id) REFERENCES seance(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_seance_equipement_equipement
        FOREIGN KEY (idEquipement) REFERENCES equipement(idEquipement)
        ON DELETE CASCADE
);
