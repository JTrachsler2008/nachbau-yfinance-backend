ALTER TABLE portfolios ADD COLUMN manager_user_id BIGINT NULL;
ALTER TABLE portfolios ADD CONSTRAINT fk_portfolios_manager
    FOREIGN KEY (manager_user_id) REFERENCES users(id);
