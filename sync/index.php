<?php
declare(strict_types=1);

/**
 * Personal Kanban — sync API (single-file, no framework).
 *
 * Endpoints (base URL is this file):
 *   GET  ?action=get&board_id=<uuid>   -> board snapshot or {"found":false}
 *   POST ?action=put                   -> create/update a board
 *   GET  ?action=catalog               -> list of known boards
 *
 * The desktop app sends its API key in the X-API-Key header.
 * Concurrency is optimistic: a push carries the base_version the
 * client last saw; the server accepts it only when it still matches
 * the stored version (base_version -1 forces the overwrite, used to
 * resolve conflicts explicitly). Every accepted write bumps the
 * version and stamps the server clock, so "newer" is decided by the
 * server, never by client clocks.
 */

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, array $body): void {
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function fail(int $status, string $error, string $detail = ''): void {
    $body = ['ok' => false, 'error' => $error];
    if ($detail !== '') {
        $body['detail'] = $detail;
    }
    respond($status, $body);
}

// ---------------------------------------------------------------- config

if (!is_file(__DIR__ . '/config.php')) {
    fail(500, 'not_configured', 'config.php is missing (copy config.sample.php)');
}
/** @var array{db_dsn:string,db_user:string,db_password:string,api_key_hash:string,max_payload_bytes:int} $config */
$config = require __DIR__ . '/config.php';

// ---------------------------------------------------------------- auth

$headerKey = $_SERVER['HTTP_X_API_KEY'] ?? $_SERVER['REDIRECT_HTTP_X_API_KEY'] ?? '';
if ($headerKey === '' || !hash_equals(
    (string) $config['api_key_hash'],
    'sha256:' . hash('sha256', $headerKey)
)) {
    fail(401, 'unauthorized');
}

// ---------------------------------------------------------------- database

try {
    $pdo = new PDO(
        (string) $config['db_dsn'],
        (string) $config['db_user'],
        (string) $config['db_password'],
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]
    );
} catch (PDOException $e) {
    fail(500, 'db_unavailable', 'database connection failed');
}

$action = (string) ($_GET['action'] ?? '');
$method = $_SERVER['REQUEST_METHOD'] ?? '';

try {
    switch (true) {
        case $method === 'GET' && $action === 'get':
            getBoard($pdo);
            break;
        case $method === 'POST' && $action === 'put':
            putBoard($pdo, $config);
            break;
        case $method === 'GET' && $action === 'catalog':
            listCatalog($pdo);
            break;
        default:
            fail(404, 'unknown_action', 'action must be get, put or catalog');
    }
} catch (Throwable $e) {
    fail(500, 'internal_error', $e->getMessage());
}

// ---------------------------------------------------------------- handlers

function boardIdParam(): string {
    $id = (string) ($_GET['board_id'] ?? '');
    if (!preg_match('/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/', $id)) {
        fail(400, 'bad_board_id', 'board_id must be a UUID');
    }
    return $id;
}

function getBoard(PDO $pdo): void {
    $id = boardIdParam();
    $statement = $pdo->prepare(
        'SELECT id, name, version, updated_at, payload FROM sync_board WHERE id = ?'
    );
    $statement->execute([$id]);
    $row = $statement->fetch();
    if ($row === false) {
        respond(200, ['ok' => true, 'found' => false, 'board_id' => $id]);
    }
    respond(200, [
        'ok' => true,
        'found' => true,
        'board' => [
            'id' => $row['id'],
            'name' => $row['name'],
            'version' => (int) $row['version'],
            'updated_at' => (int) $row['updated_at'],
            'payload' => json_decode((string) $row['payload'], true),
        ],
    ]);
}

function putBoard(PDO $pdo, array $config): void {
    $input = json_decode((string) file_get_contents('php://input'), true);
    if (!is_array($input)) {
        fail(400, 'bad_json', 'request body must be a JSON object');
   
    }
    $id = (string) ($input['board_id'] ?? '');
    $name = (string) ($input['name'] ?? '');
    $baseVersion = $input['base_version'] ?? null;
    $payload = $input['payload'] ?? null;

    if (!preg_match('/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/', $id)) {
        fail(400, 'bad_board_id', 'board_id must be a UUID');
    }
    if ($name === '' || strlen($name) > 200) {
        fail(400, 'bad_name', 'name must be 1..200 characters');
    }
    if (!is_int($baseVersion)) {
        fail(400, 'bad_version', 'base_version must be an integer (-1 to force)');
    }
    if ($payload === null || !is_array($payload)) {
        fail(400, 'bad_payload', 'payload must be the BoardMemento JSON object');
    }
    $encoded = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($encoded === false || strlen($encoded) > (int) $config['max_payload_bytes']) {
        fail(413, 'payload_too_large', 'payload exceeds the server limit');
    }

    $now = (int) round(microtime(true) * 1000);

    $statement = $pdo->prepare(
        'SELECT version FROM sync_board WHERE id = ?'
    );
    $statement->execute([$id]);
    $current = $statement->fetchColumn();

    if ($current === false) {
        // New board: only accepted when the client had nothing (or forces).
        if ($baseVersion !== 0 && $baseVersion !== -1) {
            respond(409, [
                'ok' => false,
                'error' => 'conflict',
                'detail' => 'board already exists remotely',
                'board' => null,
            ]);
        }
        $insert = $pdo->prepare(
            'INSERT INTO sync_board (id, name, version, updated_at, payload) VALUES (?, ?, 1, ?, ?)'
        );
        $insert->execute([$id, $name, $now, $encoded]);
        respond(200, ['ok' => true, 'version' => 1, 'updated_at' => $now]);
        return;
    }

    $currentVersion = (int) $current;
    if ($baseVersion !== $currentVersion && $baseVersion !== -1) {
        // Stale push: send the current state back so the client can merge.
        $row = $pdo->prepare(
            'SELECT id, name, version, updated_at, payload FROM sync_board WHERE id = ?'
        );
        $row->execute([$id]);
        $remote = $row->fetch();
        respond(409, [
            'ok' => false,
            'error' => 'conflict',
            'detail' => 'base_version does not match',
            'board' => [
                'id' => $remote['id'],
                'name' => $remote['name'],
                'version' => (int) $remote['version'],
                'updated_at' => (int) $remote['updated_at'],
                'payload' => json_decode((string) $remote['payload'], true),
            ],
        ]);
    }

    $update = $pdo->prepare(
        'UPDATE sync_board SET name = ?, version = version + 1, updated_at = ?, payload = ? WHERE id = ?'
    );
    $update->execute([$name, $now, $encoded, $id]);
    respond(200, [
        'ok' => true,
        'version' => $currentVersion + 1,
        'updated_at' => $now,
    ]);
}

function listCatalog(PDO $pdo): void {
    $statement = $pdo->query(
        'SELECT id, name, version, updated_at FROM sync_board ORDER BY updated_at DESC, id ASC'
    );
    $boards = [];
    foreach ($statement->fetchAll() as $row) {
        $boards[] = [
            'id' => $row['id'],
            'name' => $row['name'],
            'version' => (int) $row['version'],
            'updated_at' => (int) $row['updated_at'],
        ];
    }
    respond(200, ['ok' => true, 'boards' => $boards]);
}
