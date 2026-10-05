<?php
// Personal Kanban — sync server configuration.
// Copy this file to config.php and fill in the values. Keep config.php
// out of version control; the API key below is stored as a hash, so the
// plaintext key only ever lives on your devices.
//
// Generate the hash with:
//   php -r "echo hash('sha256', 'TU_CLAVE_SECRETA');"
// and paste the output (with the sha256: prefix) below.

return [
    // MySQL / MariaDB connection
    'db_dsn'      => 'mysql:host=localhost;dbname=personalkanban;charset=utf8mb4',
    'db_user'     => 'kanban',
    'db_password' => 'CAMBIA_ESTA_CONTRASENA',

    // Expected SHA-256 of the API key the desktop app sends in X-API-Key.
    'api_key_hash' => 'sha256:REEMPLAZA_CON_EL_HASH_DE_TU_CLAVE',

    // Maximum accepted payload size in bytes (5 MB by default).
    'max_payload_bytes' => 5 * 1024 * 1024,
];
