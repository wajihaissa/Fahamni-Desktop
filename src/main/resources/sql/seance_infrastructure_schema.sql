ALTER TABLE seance
    ADD COLUMN IF NOT EXISTS mode_seance VARCHAR(30) NOT NULL DEFAULT 'en_ligne';

ALTER TABLE seance
    ADD COLUMN IF NOT EXISTS salle_id INT NULL;

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

CREATE TABLE IF NOT EXISTS reservation_place (
    idReservationPlace INT AUTO_INCREMENT PRIMARY KEY,
    idReservation INT NOT NULL,
    idSeance INT NOT NULL,
    idPlace INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_place_reservation
        FOREIGN KEY (idReservation) REFERENCES reservation(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reservation_place_seance
        FOREIGN KEY (idSeance) REFERENCES seance(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reservation_place_place
        FOREIGN KEY (idPlace) REFERENCES place(idPlace)
        ON DELETE RESTRICT,
    CONSTRAINT uk_reservation_place_reservation UNIQUE (idReservation),
    CONSTRAINT uk_reservation_place_seance_place UNIQUE (idSeance, idPlace)
);
