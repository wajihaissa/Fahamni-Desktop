-- Interaction schema update
-- Aligns table `interaction` with the columns expected by the Blog module.

SET @db_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'interaction'
              AND COLUMN_NAME = 'is_notif_read'
        ),
        'SELECT ''is_notif_read already exists''',
        'ALTER TABLE interaction ADD COLUMN is_notif_read TINYINT(1) NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'interaction'
              AND COLUMN_NAME = 'is_flagged'
        ),
        'SELECT ''is_flagged already exists''',
        'ALTER TABLE interaction ADD COLUMN is_flagged TINYINT(1) NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'interaction'
              AND COLUMN_NAME = 'is_deleted_by_admin'
        ),
        'SELECT ''is_deleted_by_admin already exists''',
        'ALTER TABLE interaction ADD COLUMN is_deleted_by_admin TINYINT(1) NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
