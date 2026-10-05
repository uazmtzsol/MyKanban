package com.personalkanban.infrastructure.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalkanban.application.sync.PushResult;
import com.personalkanban.application.sync.RemoteBoard;
import com.personalkanban.application.sync.RemoteBoardInfo;
import com.personalkanban.application.sync.SyncException;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardLink;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.EntryId;
import com.personalkanban.domain.board.Ids;
import com.personalkanban.domain.board.TimelineEntry;
import com.personalkanban.domain.board.WipLimit;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link HttpSyncRepository} against an in-process
 * HTTP server that speaks the same wire protocol as the PHP
 * API (sync/index.php): get / put / catalog, X-API-Key auth,
 * optimistic versioning with 409 carrying the remote board.
 */
class HttpSyncRepositoryTest {

    private static final String API_KEY = "test-key-12345";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private final Map<String, StoredBoard> store = new ConcurrentHashMap<>();
    private final AtomicLong clock = new AtomicLong(1_700_000_000_000L);

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        server.createContext("/api", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ------------------------------------------------------------------
    // Behavior
    // ------------------------------------------------------------------

    @Test
    void fetchUnknownBoardIsEmpty() {
        HttpSyncRepository repository = new HttpSyncRepository(baseUrl, API_KEY);

        assertThat(repository.fetch(new BoardId(newUuid()))).isEmpty();
    }

    @Test
    void pushCreatesAndFetchRoundTripsTheWholeMemento() {
        HttpSyncRepository repository = new HttpSyncRepository(baseUrl, API_KEY);
        BoardId boardId = new BoardId(newUuid());
        BoardMemento memento = sampleMemento();

        PushResult result = repository.push(boardId, "Tablero", memento, 0, false);
        assertThat(result).isInstanceOf(PushResult.Ok.class);
        PushResult.Ok ok = (PushResult.Ok) result;
        assertThat(ok.version()).isEqualTo(1);

        RemoteBoard remote = repository.fetch(boardId).orElseThrow();
        assertThat(remote.id()).isEqualTo(boardId);
        assertThat(remote.name()).isEqualTo("Tablero");
        assertThat(remote.version()).isEqualTo(1);

        // The full memento survives the wire, including links and timeline.
        BoardMemento fetched = remote.memento();
        assertThat(fetched.columns()).hasSize(1);
        ColumnSnapshot column = fetched.columns().get(0);
        assertThat(column.title()).isEqualTo("To do");
        assertThat(column.description()).isEqualTo("Column description");
        assertThat(column.color()).isEqualTo(BoardColor.BLUE);
        assertThat(column.wipLimit().asOptional()).contains(3);
        assertThat(column.done()).isFalse();
        assertThat(column.backgroundColor()).isEqualTo("#ffffff");
        assertThat(column.cards()).hasSize(2);

        CardSnapshot first = column.cards().get(0);
        assertThat(first.title()).isEqualTo("Card 1");
        assertThat(first.description()).isEqualTo("Details");
        assertThat(first.color()).isEqualTo(BoardColor.GREEN);
        assertThat(first.dueDate()).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(first.labels()).containsExactly("bug");
        assertThat(first.notes()).isEqualTo("some notes");
        assertThat(first.checklist())
                .containsExactly(new ChecklistItem("ci-1", "Check me", false));
        assertThat(first.processId()).isNull();

        assertThat(fetched.links())
                .containsExactly(new CardLink(first.id(), column.cards().get(1).id()));
        assertThat(fetched.timeline()).hasSize(1);
        assertThat(fetched.timeline().get(0).cardId()).isEqualTo(first.id());
        assertThat(fetched.timeline().get(0).comment()).isEqualTo("working on it");
    }

    @Test
    void pushWithCurrentVersionUpdates() {
        HttpSyncRepository repository = new HttpSyncRepository(baseUrl, API_KEY);
        BoardId boardId = new BoardId(newUuid());
        repository.push(boardId, "Tablero", BoardMemento.empty(), 0, false);

        PushResult result = repository.push(boardId, "Tablero renombrado",
                BoardMemento.empty(), 1, false);

        assertThat(result).isInstanceOf(PushResult.Ok.class);
        assertThat(((PushResult.Ok) result).version()).isEqualTo(2);
        assertThat(repository.fetch(boardId).orElseThrow().name())
                .isEqualTo("Tablero renombrado");
    }

    @Test
    void stalePushIsRejectedWithTheRemoteState() {
        HttpSyncRepository repository = new HttpSyncRepository(baseUrl, API_KEY);
        BoardId boardId = new BoardId(newUuid());
        repository.push(boardId, "Tablero A", BoardMemento.empty(), 0, false);
        // Another device advances the board to version 2.
        repository.push(boardId, "Tablero B", BoardMemento.empty(), 1, false);

        // This device still thinks it is on version 1.
        PushResult result = repository.push(boardId, "Tablero A otra vez",
                BoardMemento.empty(), 1, false);

        assertThat(result).isInstanceOf(PushResult.Conflict.class);
        PushResult.Conflict conflict = (PushResult.Conflict) result;
        assertThat(conflict.remote()).isPresent();
        RemoteBoard remote = conflict.remote().orElseThrow();
        assertThat(remote.name()).isEqualTo("Tablero B");
        assertThat(remote.version()).isEqualTo(2);
        // The server kept its own version.
        assertThat(repository.fetch(boardId).orElseThrow().name())
                .isEqualTo("Tablero B");
    }

    @Test
    void forcedPushOverwrites() {
        HttpSyncRepository repository = new HttpSyncRepository(baseUrl, API_KEY);
        BoardId boardId = new BoardId(newUuid());
        repository.push(boardId, "Tablero A", BoardMemento.empty(), 0, false);
        repository.push(boardId, "Tablero B", BoardMemento.empty(), 1, false);

        PushResult result = repository.push(boardId, "Tablero A gana",
                BoardMemento.empty(), 1, true);

        assertThat(result).isInstanceOf(PushResult.Ok.class);
        assertThat(((PushResult.Ok) result).version()).isEqualTo(3);
        assertThat(repository.fetch(boardId).orElseThrow().name())
                .isEqualTo("Tablero A gana");
    }

    @Test
    void catalogListsBoardsNewestFirst() {
        HttpSyncRepository repository = new HttpSyncRepository(baseUrl, API_KEY);
        BoardId first = new BoardId(newUuid());
        BoardId second = new BoardId(newUuid());
        repository.push(first, "Primer tablero", BoardMemento.empty(), 0, false);
        repository.push(second, "Segundo tablero", BoardMemento.empty(), 0, false);

        List<RemoteBoardInfo> boards = repository.catalog();

        assertThat(boards).extracting(RemoteBoardInfo::name)
                .containsExactly("Segundo tablero", "Primer tablero");
        assertThat(boards).extracting(RemoteBoardInfo::id)
                .containsExactly(second, first);
    }

    @Test
    void wrongApiKeyIsRejected() {
        HttpSyncRepository repository = new HttpSyncRepository(baseUrl, "wrong-key");

        assertThatThrownBy(() -> repository.fetch(new BoardId(newUuid())))
                .isInstanceOf(SyncException.class)
                .hasFieldOrPropertyWithValue("kind", SyncException.Kind.UNAUTHORIZED)
                .hasFieldOrPropertyWithValue("statusCode", 401);
    }

    @Test
    void invalidNameIsRejectedAsBadRequest() {
        HttpSyncRepository repository = new HttpSyncRepository(baseUrl, API_KEY);

        assertThatThrownBy(() -> repository.push(new BoardId(newUuid()), "",
                BoardMemento.empty(), 0, false))
                .isInstanceOf(SyncException.class)
                .hasFieldOrPropertyWithValue("kind", SyncException.Kind.BAD_REQUEST)
                .hasFieldOrPropertyWithValue("statusCode", 400);
    }

    @Test
    void unreachableServerReportsNetworkFailure() throws Exception {
        // Reserve a port and immediately free it again.
        HttpServer doomed = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String deadUrl = "http://127.0.0.1:" + doomed.getAddress().getPort() + "/api";
        doomed.stop(0);
        HttpSyncRepository repository = new HttpSyncRepository(deadUrl, API_KEY);

        assertThatThrownBy(() -> repository.fetch(new BoardId(newUuid())))
                .isInstanceOf(SyncException.class)
                .hasFieldOrPropertyWithValue("kind", SyncException.Kind.NETWORK)
                .hasFieldOrPropertyWithValue("statusCode", 0);
    }

    // ------------------------------------------------------------------
    // Fake sync API (same wire contract as sync/index.php)
    // ------------------------------------------------------------------

    private void handle(HttpExchange exchange) {
        try {
            if (!API_KEY.equals(exchange.getRequestHeaders()
                    .getFirst("X-API-Key"))) {
                respond(exchange, 401, "{\"ok\":false,\"error\":\"unauthorized\"}");
                return;
            }
            Map<String, String> params = splitQuery(
                    exchange.getRequestURI().getQuery());
            String action = params.getOrDefault("action", "");
            switch (action) {
                case "get" -> getBoard(exchange, params.getOrDefault("board_id", ""));
                case "put" -> putBoard(exchange, parse(requestBody(exchange)));
                case "catalog" -> catalog(exchange);
                default -> respond(exchange, 404,
                        "{\"ok\":false,\"error\":\"unknown_action\"}");
            }
        } catch (IOException e) {
            // The client hung up mid-response; nothing to report.
        } catch (RuntimeException e) {
            try {
                respond(exchange, 500,
                        "{\"ok\":false,\"error\":\"internal_error\"}");
            } catch (IOException ignored) {
                // giving up on this exchange is fine
            }
        }
    }

    private void getBoard(HttpExchange exchange, String boardId) throws IOException {
        if (!isUuid(boardId)) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"bad_board_id\"}");
            return;
        }
        StoredBoard stored = store.get(boardId);
        if (stored == null) {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("ok", true);
            root.put("found", false);
            root.put("board_id", boardId);
            respond(exchange, 200, root);
            return;
        }
        respond(exchange, 200, boardNode(boardId, stored));
    }

    private void putBoard(HttpExchange exchange, JsonNode input) throws IOException {
        if (input.isMissingNode()) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"bad_json\"}");
            return;
        }
        String boardId = input.path("board_id").asText("");
        String name = input.path("name").asText("");
        if (!isUuid(boardId)) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"bad_board_id\"}");
            return;
        }
        if (name.isBlank() || name.length() > 200) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"bad_name\"}");
            return;
        }
        JsonNode baseVersion = input.path("base_version");
        if (!baseVersion.isInt()) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"bad_version\"}");
            return;
        }
        JsonNode payload = input.path("payload");
        if (!payload.isObject()) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"bad_payload\"}");
            return;
        }            StoredBoard current = store.get(boardId);
        if (current == null) {
            if (baseVersion.asInt() != 0 && baseVersion.asInt() != -1) {
                respond(exchange, 409, conflictNode(null));
                return;
            }
            StoredBoard created = new StoredBoard(boardId, name, 1, tick(), payload);
            store.put(boardId, created);
            respond(exchange, 200, okNode(created));
            return;
        }
        if (baseVersion.asInt() != current.version() && baseVersion.asInt() != -1) {
            respond(exchange, 409, conflictNode(boardObject(boardId, current)));
            return;
        }
        StoredBoard updated = new StoredBoard(boardId, name, current.version() + 1, tick(), payload);
        store.put(boardId, updated);
        respond(exchange, 200, okNode(updated));
    }

    private void catalog(HttpExchange exchange) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("ok", true);
        var boards = root.putArray("boards");
        store.values().stream()
                .sorted((a, b) -> Long.compare(b.updatedAt(), a.updatedAt()))
                .forEach(stored -> {
                    ObjectNode node = boards.addObject();
                    node.put("id", stored.id());
                    node.put("name", stored.name());
                    node.put("version", stored.version());
                    node.put("updated_at", stored.updatedAt());
                });
        respond(exchange, 200, root);
    }

    private ObjectNode boardNode(String boardId, StoredBoard stored) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("ok", true);
        root.put("found", true);
        root.set("board", boardObject(boardId, stored));
        return root;
    }

    private ObjectNode boardObject(String boardId, StoredBoard stored) {
        ObjectNode board = MAPPER.createObjectNode();
        board.put("id", boardId);
        board.put("name", stored.name());
        board.put("version", stored.version());
        board.put("updated_at", stored.updatedAt());
        board.set("payload", stored.payload());
        return board;
    }

    private ObjectNode okNode(StoredBoard stored) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("ok", true);
        root.put("version", stored.version());
        root.put("updated_at", stored.updatedAt());
        return root;
    }

    private ObjectNode conflictNode(ObjectNode board) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("ok", false);
        root.put("error", "conflict");
        root.put("detail", "base_version does not match");
        root.set("board", board);
        return root;
    }

    private long tick() {
        return clock.incrementAndGet();
    }

    private void respond(HttpExchange exchange, int status, ObjectNode body)
            throws IOException {
        respond(exchange, status, write(body));
    }

    private void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String write(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String requestBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return "{}";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("bad_json", e);
        }
    }

    private static Map<String, String> splitQuery(String query) {
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        java.util.HashMap<String, String> params = new java.util.HashMap<>();
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            params.put(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    private static boolean isUuid(String value) {
        return value != null && value.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    private static String newUuid() {
        return UUID.randomUUID().toString();
    }

    /** A memento exercising every corner of the wire format. */
    private static BoardMemento sampleMemento() {
        ColumnId columnId = Ids.newColumnId();
        var first = Ids.newCardId();
        var second = Ids.newCardId();
        ColumnSnapshot column = new ColumnSnapshot(
                columnId, "To do", "Column description", BoardColor.BLUE,
                WipLimit.of(3), Instant.parse("2026-01-01T00:00:00Z"),
                false, "#ffffff",
                List.of(
                        new CardSnapshot(first, "Card 1", "Details", BoardColor.GREEN,
                                LocalDate.parse("2026-02-01"), List.of("bug"),
                                Instant.parse("2026-01-01T00:00:00Z"), "some notes",
                                List.of(new ChecklistItem("ci-1", "Check me", false)), null),
                        new CardSnapshot(second, "Card 2", "", BoardColor.RED,
                                null, List.of(), Instant.parse("2026-01-02T00:00:00Z"))));
        return new BoardMemento(
                List.of(column),
                List.of(),
                List.of(new CardLink(first, second)),
                List.of(TimelineEntry.restore(first, new EntryId("e-1"),
                        Instant.parse("2026-01-03T10:00:00Z"), null, "working on it")));
    }

    /** Server-side state of one remote board. */
    private record StoredBoard(String id, String name, long version,
                                long updatedAt, JsonNode payload) {
    }
}
