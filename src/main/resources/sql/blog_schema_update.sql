-- Blog schema update
-- Aligns table `blog` with the columns expected by the merged Blog module.

SET @db_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'blog'
              AND COLUMN_NAME = 'category'
        ),
        'SELECT ''category already exists''',
        'ALTER TABLE blog ADD COLUMN category VARCHAR(100) NULL AFTER publisher_id'
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
              AND TABLE_NAME = 'blog'
              AND COLUMN_NAME = 'likes_count'
        ),
        'SELECT ''likes_count already exists''',
        'ALTER TABLE blog ADD COLUMN likes_count INT NOT NULL DEFAULT 0 AFTER category'
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
              AND TABLE_NAME = 'blog'
              AND COLUMN_NAME = 'comments_count'
        ),
        'SELECT ''comments_count already exists''',
        'ALTER TABLE blog ADD COLUMN comments_count INT NOT NULL DEFAULT 0 AFTER likes_count'
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
              AND TABLE_NAME = 'blog'
              AND COLUMN_NAME = 'is_status_notif_read'
        ),
        'SELECT ''is_status_notif_read already exists''',
        'ALTER TABLE blog ADD COLUMN is_status_notif_read TINYINT(1) NOT NULL DEFAULT 0 AFTER comments_count'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Preserve old category information when `images` was previously used to store it.
UPDATE blog
SET category = images
WHERE (category IS NULL OR category = '')
  AND images IN (
      'mathematics',
      'science',
      'computer-science',
      'study-tips',
      'language',
      'math',
      'physique',
      'informatique',
      'langue',
      'autre'
  );

-- Rebuild counters from the interaction table.
UPDATE blog b
SET likes_count = (
    SELECT COUNT(*)
    FROM interaction i
    WHERE i.blog_id = b.id
      AND i.reaction IS NOT NULL
      AND i.reaction <> 0
);

UPDATE blog b
SET comments_count = (
    SELECT COUNT(*)
    FROM interaction i
    WHERE i.blog_id = b.id
      AND i.comment IS NOT NULL
      AND i.comment <> ''
);
