-- Personal Kanban — sync server schema (MySQL 5.7+ / MariaDB 10.2+)
-- Run once:  mysql -u root -p personalkanban < setup.sql
-- The database itself must exist beforehand:
--   CREATE DATABASE personalkanban CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sync_board (
    id         CHAR(36)    NOT NULL,          -- client-generated UUID (BoardId)
    name       VARCHAR(200) NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    updated_at BIGINT       NOT NULL DEFAULT 0,  -- epoch milliseconds, server clock
    payload    LONGTEXT     NOT NULL,          -- BoardMemento as JSON
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sync_meta (
    id          INT          NOT NULL AUTO_INCREMENT,
    created_at  BIGINT       NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
