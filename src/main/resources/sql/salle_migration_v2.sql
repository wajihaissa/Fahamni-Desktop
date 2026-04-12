ALTER TABLE salle
    ADD COLUMN batiment VARCHAR(150) NULL AFTER description,
    ADD COLUMN etage INT NULL AFTER batiment,
    ADD COLUMN typeDisposition VARCHAR(100) NULL AFTER etage,
    ADD COLUMN accesHandicape BOOLEAN NOT NULL DEFAULT FALSE AFTER typeDisposition,
    ADD COLUMN statutDetaille VARCHAR(150) NULL AFTER accesHandicape,
    ADD COLUMN dateDerniereMaintenance DATE NULL AFTER statutDetaille;
