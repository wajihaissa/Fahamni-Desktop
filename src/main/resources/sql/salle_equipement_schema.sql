CREATE TABLE IF NOT EXISTS salle (
    idSalle INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(150) NOT NULL,
    capacite INT NOT NULL,
    localisation VARCHAR(255) NOT NULL,
    typeSalle VARCHAR(100) NOT NULL,
    etat VARCHAR(50) NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS equipement (
    idEquipement INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(150) NOT NULL,
    typeEquipement VARCHAR(100) NOT NULL,
    quantiteDisponible INT NOT NULL,
    etat VARCHAR(50) NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS place (
    idPlace INT AUTO_INCREMENT PRIMARY KEY,
    numero INT NOT NULL,
    rang INT NOT NULL,
    colonne INT NOT NULL,
    etat VARCHAR(50) NOT NULL,
    idSalle INT NOT NULL,
    CONSTRAINT fk_place_salle
        FOREIGN KEY (idSalle) REFERENCES salle(idSalle)
        ON DELETE CASCADE,
    CONSTRAINT uk_place_salle_numero UNIQUE (idSalle, numero),
    CONSTRAINT uk_place_salle_position UNIQUE (idSalle, rang, colonne)
);

CREATE TABLE IF NOT EXISTS salle_equipement (
    idSalle INT NOT NULL,
    idEquipement INT NOT NULL,
    quantite INT NOT NULL,
    PRIMARY KEY (idSalle, idEquipement),
    CONSTRAINT fk_salle_equipement_salle
        FOREIGN KEY (idSalle) REFERENCES salle(idSalle)
        ON DELETE CASCADE,
    CONSTRAINT fk_salle_equipement_equipement
        FOREIGN KEY (idEquipement) REFERENCES equipement(idEquipement)
        ON DELETE CASCADE
);
