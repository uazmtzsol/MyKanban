package com.personalkanban.infrastructure.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalkanban.application.BoardJsonMapper;
import com.personalkanban.application.port.SyncRepository;
import com.personalkanban.application.sync.PushResult;
import com.personalkanban.application.sync.RemoteBoard;
import com.personalkanban.application.sync.RemoteBoardInfo;
import com.personalkanban.application.sync.SyncException;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link SyncRepository} backed by the single-file PHP API
 * ({@code sync/index.php}): get / put / catalog over plain
 * HTTP, authenticating with an {@code X-API-Key} header.
 *
 * <p>Uses {@code java.net.http} from the JDK 11+ — no new
 * dependency. The board travels as the {@link BoardMemento}
 * JSON produced by the shared {@link BoardJsonMapper}, so the
 * wire format is identical to the undo history and the
 * export/import format.</p>
 *
 * <p>Concurrency is optimistic: a push carries the version
 * the client last saw and the server answers 409 (mapped to
 * {@link PushResult.Conflict}) when another device wrote
 * first. Every accepted write bumps the server-side version
 * and stamps the server clock.</p>
 */
public final class HttpSyncRepository implements SyncRepository {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client;
    private final URI baseUri;
    private final String apiKey;
    private final ObjectMapper mapper;

    public HttpSyncRepository(String baseUrl, String apiKey) {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build(),
                baseUrl, apiKey, BoardJsonMapper.create());
    }

    /** Test seam: inject the client and the JSON mapper. */
    public HttpSyncRepository(HttpClient client, String baseUrl,
                                String apiKey, ObjectMapper mapper) {
        this.client = client;
        this.baseUri = normalize(baseUrl);
        this.apiKey = apiKey;
        this.mapper = mapper;
    }

    @Override
    public Optional<RemoteBoard> fetch(BoardId boardId) {
        Response response = send(get("?action=get&board_id=" + encode(boardId.value())));
        if (response.status() == 401) {
            throw unauthorized();
        }
        if (response.status() != 200) {
            throw unexpected(response);
        }
        JsonNode body = response.body();
        if (!body.path("found").asBoolean(false)) {
            return Optional.empty();
        }
        JsonNode board = body.path("board");
        return board.isMissingNode() || board.isNull()
                ? Optional.empty()
                : Optional.of(parseBoard(board));
    }

    @Override
    public PushResult push(BoardId boardId, String name, BoardMemento memento,
                            long baseVersion, boolean force) {
        ObjectNode request = mapper.createObjectNode();
        request.put("board_id", boardId.value());
        request.put("name", name);
        request.put("base_version", force ? -1 : baseVersion);
        request.set("payload", mapper.valueToTree(memento));

        HttpRequest.Builder http = post("?action=put")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(request)));
        Response response = send(http);
        return switch (response.status()) {
            case 200 -> new PushResult.Ok(
                    response.body().path("version").asLong(),
                    Instant.ofEpochMilli(response.body().path("updated_at").asLong()));
            case 409 -> new PushResult.Conflict(remoteOf(response.body()));
            case 401 -> throw unauthorized();
            case 400 -> throw new SyncException(
                    SyncException.Kind.BAD_REQUEST, 400, detail(response.body()));
            case 413 -> throw new SyncException(
                    SyncException.Kind.TOO_LARGE, 413, detail(response.body()));
            default -> throw unexpected(response);
        };
    }

    @Override
    public List<RemoteBoardInfo> catalog() {
        Response response = send(get("?action=catalog"));
        if (response.status() == 401) {
            throw unauthorized();
        }
        if (response.status() != 200) {
            throw unexpected(response);
        }
        List<RemoteBoardInfo> boards = new ArrayList<>();
        for (JsonNode node : response.body().path("boards")) {
            boards.add(new RemoteBoardInfo(
                    new BoardId(text(node, "id")),
                    text(node, "name"),
                    node.path("version").asLong(),
                    Instant.ofEpochMilli(node.path("updated_at").asLong())));
        }
        return boards;
    }

    // ------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------

    private HttpRequest.Builder get(String query) {
        return HttpRequest.newBuilder(URI.create(baseUri + query));
    }

    private HttpRequest.Builder post(String query) {
        return HttpRequest.newBuilder(URI.create(baseUri + query));
    }

    /** Sends the request and parses the JSON body (empty body = {}). */
    private Response send(HttpRequest.Builder request) {
        HttpRequest http = request
                .header("X-API-Key", apiKey)
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(http, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SyncException(SyncException.Kind.NETWORK, 0,
                    "The sync server could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyncException(SyncException.Kind.NETWORK, 0,
                    "Sync was interrupted", e);
        }
        JsonNode body;
        try {
            body = response.body() == null || response.body().isBlank()
                    ? mapper.createObjectNode()
                    : mapper.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new SyncException(SyncException.Kind.SERVER, response.statusCode(),
                    "The sync server replied with something that is not JSON", e);
        }
        return new Response(response.statusCode(), body);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String toJson(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            // Serializing a tree we just built cannot fail.
            throw new IllegalStateException(e);
        }
    }

    private static URI normalize(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Sync server URL must not be blank");
        }
        String trimmed = baseUrl.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return URI.create(trimmed);
    }

    // ------------------------------------------------------------------
    // Response interpretation
    // ------------------------------------------------------------------

    private RemoteBoard parseBoard(JsonNode node) {
        try {
            BoardMemento memento = mapper.treeToValue(node.path("payload"), BoardMemento.class);
            return new RemoteBoard(
                    new BoardId(text(node, "id")),
                    text(node, "name"),
                    node.path("version").asLong(),
                    Instant.ofEpochMilli(node.path("updated_at").asLong()),
                    memento);
        } catch (RuntimeException | IOException e) {
            throw new SyncException(SyncException.Kind.SERVER, 200,
                    "The sync server sent a malformed board", e);
        }
    }

    private Optional<RemoteBoard> remoteOf(JsonNode body) {
        JsonNode board = body.path("board");
        return board.isMissingNode() || board.isNull()
                ? Optional.empty()
                : Optional.of(parseBoard(board));
    }

    private static SyncException unauthorized() {
        return new SyncException(SyncException.Kind.UNAUTHORIZED, 401,
                "The sync server rejected the API key");
    }

    private static SyncException unexpected(Response response) {
        return new SyncException(SyncException.Kind.SERVER, response.status(),
                "The sync server failed: " + detail(response.body()));
    }

    private static String detail(JsonNode body) {
        String error = body.path("error").asText("sync failed");
        String text = body.path("detail").asText("");
        return text.isBlank() ? error : error + ": " + text;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return value.asText();
    }

    /** Parsed response: HTTP status plus the (possibly empty) JSON body. */
    private record Response(int status, JsonNode body) {
    }
}
