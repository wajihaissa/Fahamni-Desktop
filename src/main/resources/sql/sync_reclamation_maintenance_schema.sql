-- Syncs the live `reclamation_salle` and `maintenance_salle` tables with the
-- schema expected by the infrastructure module and service layer.
--
-- This script is designed for the current database state observed on 2026-04-13:
-- - `reclamation_salle` uses legacy columns like `id`, `titre`, `categorie`
-- - `maintenance_salle` uses legacy columns like `id`, `titre`, `assigneeA`
--
-- Recommended: back up the database before running this script.

SET @db_name = DATABASE();

-- ---------------------------------------------------------------------------
-- Reclamation schema alignment
-- ---------------------------------------------------------------------------

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

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'id'
        )
        AND NOT EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'idReclamation'
        ),
        'ALTER TABLE reclamation_salle CHANGE COLUMN id idReclamation INT NOT NULL AUTO_INCREMENT',
        'SELECT ''reclamation_salle id already aligned'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'nomDeclarant'
        ),
        'ALTER TABLE reclamation_salle ADD COLUMN nomDeclarant VARCHAR(150) NULL AFTER idSalle',
        'SELECT ''nomDeclarant already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE reclamation_salle
SET nomDeclarant = COALESCE(NULLIF(TRIM(nomDeclarant), ''), 'Inconnu');

ALTER TABLE reclamation_salle
    MODIFY COLUMN nomDeclarant VARCHAR(150) NOT NULL;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'referenceSeance'
        ),
        'ALTER TABLE reclamation_salle ADD COLUMN referenceSeance VARCHAR(150) NULL AFTER nomDeclarant',
        'SELECT ''referenceSeance already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'categorie'
        )
        AND NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'typeProbleme'
        ),
        'ALTER TABLE reclamation_salle CHANGE COLUMN categorie typeProbleme VARCHAR(100) NULL',
        'SELECT ''typeProbleme already exists or categorie missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'typeProbleme'
        ),
        'ALTER TABLE reclamation_salle ADD COLUMN typeProbleme VARCHAR(100) NULL AFTER referenceSeance',
        'SELECT ''typeProbleme already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE reclamation_salle
SET typeProbleme = COALESCE(NULLIF(TRIM(typeProbleme), ''), 'autre');

ALTER TABLE reclamation_salle
    MODIFY COLUMN typeProbleme VARCHAR(100) NOT NULL;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'commentaireAdmin'
        ),
        'ALTER TABLE reclamation_salle ADD COLUMN commentaireAdmin TEXT NULL AFTER description',
        'SELECT ''commentaireAdmin already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'traite_par'
        ),
        'UPDATE reclamation_salle
         SET commentaireAdmin = COALESCE(
             NULLIF(commentaireAdmin, ''''),
             CASE
                 WHEN traite_par IS NULL OR TRIM(traite_par) = '''' THEN NULL
                 ELSE CONCAT(''Traite par: '', traite_par)
             END
         )',
        'SELECT ''traite_par not present'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'created_at'
        )
        AND NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'dateReclamation'
        ),
        'ALTER TABLE reclamation_salle CHANGE COLUMN created_at dateReclamation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
        'SELECT ''dateReclamation already exists or created_at missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'dateReclamation'
        ),
        'ALTER TABLE reclamation_salle ADD COLUMN dateReclamation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
        'SELECT ''dateReclamation already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'updated_at'
        )
        AND NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'dateTraitement'
        ),
        'ALTER TABLE reclamation_salle CHANGE COLUMN updated_at dateTraitement DATETIME NULL',
        'SELECT ''dateTraitement already exists or updated_at missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'dateTraitement'
        ),
        'ALTER TABLE reclamation_salle ADD COLUMN dateTraitement DATETIME NULL AFTER dateReclamation',
        'SELECT ''dateTraitement already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'titre'
        ),
        'UPDATE reclamation_salle
         SET description = CASE
             WHEN titre IS NULL OR TRIM(titre) = '''' THEN description
             WHEN description IS NULL OR TRIM(description) = '''' THEN titre
             WHEN description LIKE CONCAT(''['', titre, ''] %'') THEN description
             ELSE CONCAT(''['', titre, ''] '', description)
         END',
        'SELECT ''titre not present'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'titre'
        ),
        'ALTER TABLE reclamation_salle DROP COLUMN titre',
        'SELECT ''titre already removed'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'reclamation_salle'
              AND COLUMN_NAME = 'traite_par'
        ),
        'ALTER TABLE reclamation_salle DROP COLUMN traite_par',
        'SELECT ''traite_par already removed'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- Maintenance schema alignment
-- ---------------------------------------------------------------------------

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

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'id'
        )
        AND NOT EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'idMaintenance'
        ),
        'ALTER TABLE maintenance_salle CHANGE COLUMN id idMaintenance INT NOT NULL AUTO_INCREMENT',
        'SELECT ''maintenance_salle id already aligned'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'idReclamation'
        ),
        'ALTER TABLE maintenance_salle ADD COLUMN idReclamation INT NULL AFTER idSalle',
        'SELECT ''idReclamation already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'titre'
        )
        AND NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'typeMaintenance'
        ),
        'ALTER TABLE maintenance_salle CHANGE COLUMN titre typeMaintenance VARCHAR(100) NULL',
        'SELECT ''typeMaintenance already exists or titre missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'typeMaintenance'
        ),
        'ALTER TABLE maintenance_salle ADD COLUMN typeMaintenance VARCHAR(100) NULL AFTER idReclamation',
        'SELECT ''typeMaintenance already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE maintenance_salle
SET typeMaintenance = COALESCE(NULLIF(TRIM(typeMaintenance), ''), 'corrective');

ALTER TABLE maintenance_salle
    MODIFY COLUMN typeMaintenance VARCHAR(100) NOT NULL;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'assigneeA'
        )
        AND NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'responsable'
        ),
        'ALTER TABLE maintenance_salle CHANGE COLUMN assigneeA responsable VARCHAR(150) NULL',
        'SELECT ''responsable already exists or assigneeA missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'responsable'
        ),
        'ALTER TABLE maintenance_salle ADD COLUMN responsable VARCHAR(150) NULL AFTER statut',
        'SELECT ''responsable already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'description'
        )
        AND NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'detailsIntervention'
        ),
        'ALTER TABLE maintenance_salle CHANGE COLUMN description detailsIntervention TEXT NULL',
        'SELECT ''detailsIntervention already exists or description missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'detailsIntervention'
        ),
        'ALTER TABLE maintenance_salle ADD COLUMN detailsIntervention TEXT NULL AFTER responsable',
        'SELECT ''detailsIntervention already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'notes'
        ),
        'UPDATE maintenance_salle
         SET detailsIntervention = COALESCE(
             NULLIF(detailsIntervention, ''''),
             NULLIF(notes, '''')
         )',
        'SELECT ''notes not present'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'dateDemande'
        )
        AND NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'dateCreation'
        ),
        'ALTER TABLE maintenance_salle CHANGE COLUMN dateDemande dateCreation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
        'SELECT ''dateCreation already exists or dateDemande missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'dateCreation'
        ),
        'ALTER TABLE maintenance_salle ADD COLUMN dateCreation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP',
        'SELECT ''dateCreation already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'dateResolution'
        )
        AND NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'dateFin'
        ),
        'ALTER TABLE maintenance_salle CHANGE COLUMN dateResolution dateFin DATETIME NULL',
        'SELECT ''dateFin already exists or dateResolution missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'dateDebut'
        ),
        'ALTER TABLE maintenance_salle ADD COLUMN dateDebut DATE NULL AFTER datePlanifiee',
        'SELECT ''dateDebut already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'dateFin'
        ),
        'ALTER TABLE maintenance_salle MODIFY COLUMN dateFin DATE NULL',
        'SELECT ''dateFin missing'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'coutEstime'
        ),
        'ALTER TABLE maintenance_salle DROP COLUMN coutEstime',
        'SELECT ''coutEstime already removed'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'coutReel'
        ),
        'ALTER TABLE maintenance_salle DROP COLUMN coutReel',
        'SELECT ''coutReel already removed'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND COLUMN_NAME = 'notes'
        ),
        'ALTER TABLE maintenance_salle DROP COLUMN notes',
        'SELECT ''notes already removed'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND INDEX_NAME = 'uk_maintenance_salle_reclamation'
        ),
        'ALTER TABLE maintenance_salle ADD CONSTRAINT uk_maintenance_salle_reclamation UNIQUE (idReclamation)',
        'SELECT ''uk_maintenance_salle_reclamation already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'maintenance_salle'
              AND CONSTRAINT_NAME = 'fk_maintenance_salle_reclamation'
        ),
        'ALTER TABLE maintenance_salle
         ADD CONSTRAINT fk_maintenance_salle_reclamation
         FOREIGN KEY (idReclamation) REFERENCES reclamation_salle(idReclamation)
         ON DELETE SET NULL',
        'SELECT ''fk_maintenance_salle_reclamation already exists'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
