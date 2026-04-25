ALTER TABLE seance
    ADD COLUMN price_tnd DECIMAL(10,3) NULL DEFAULT NULL;

CREATE TABLE IF NOT EXISTS reservation_payment (
    reservation_id INT PRIMARY KEY,
    provider VARCHAR(30) NOT NULL DEFAULT 'konnect',
    amount_millimes INT NOT NULL,
    currency_token VARCHAR(10) NOT NULL DEFAULT 'TND',
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    payment_ref VARCHAR(120) NULL,
    payment_url TEXT NULL,
    provider_status VARCHAR(60) NULL,
    transaction_status VARCHAR(60) NULL,
    provider_payload TEXT NULL,
    initiated_at DATETIME NULL,
    last_checked_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    CONSTRAINT fk_reservation_payment_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation(id)
        ON DELETE CASCADE
);
