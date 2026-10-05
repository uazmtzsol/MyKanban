package com.personalkanban.infrastructure.sync;

import com.personalkanban.application.sync.PushResult;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check against the real PHP API (sync/index.php) served
 * by the local dev server on 127.0.0.1:8090 with the MariaDB
 * backend from sync/setup.sql. Skips itself when that stack is not
 * running, so the build stays green on machines without it.
 */
class PhpSyncApiEndpointTest {

    private static final String BASE_URL = "http://127.0.0.1:8090/index.php";
    private static final String API_KEY = "test-key-12345";

    @Test
    void javaClientSpeaksTheRealPhpApi() {
        Assumptions.assumeTrue(serverIsUp(),
                "PHP sync API is not running on 127.0.0.1:8090");
        HttpSyncRepository repository = new HttpSyncRepository(BASE_URL, API_KEY);
        BoardId boardId = new BoardId(UUID.randomUUID().toString());

        // Create remotely.
        PushResult created = repository.push(boardId, "Tablero integración",
                BoardMemento.empty(), 0, false);
        assertThat(created).isInstanceOf(PushResult.Ok.class);
        assertThat(((PushResult.Ok) created).version()).isEqualTo(1);

        // The server really stored it.
        Optional<com.personalkanban.application.sync.RemoteBoard> remote =
                repository.fetch(boardId);
        assertThat(remote).isPresent();
        assertThat(remote.orElseThrow().name()).isEqualTo("Tablero integración");

        // A stale push is answered with the remote state (optimistic lock).
        repository.push(boardId, "Avance", BoardMemento.empty(), 1, false);
        PushResult conflict = repository.push(boardId, "Tarde",
                BoardMemento.empty(), 1, false);
        assertThat(conflict).isInstanceOf(PushResult.Conflict.class);
        assertThat(((PushResult.Conflict) conflict).remote()).isPresent();
        assertThat(((PushResult.Conflict) conflict).remote().orElseThrow().version())
                .isEqualTo(2);

        // Forcing the overwrite still works.
        PushResult forced = repository.push(boardId, "Ganador",
                BoardMemento.empty(), 1, true);
        assertThat(forced).isInstanceOf(PushResult.Ok.class);
        assertThat(((PushResult.Ok) forced).version()).isEqualTo(3);

        // And the catalog knows the board.
        assertThat(repository.catalog())
                .anyMatch(info -> info.id().equals(boardId));
    }

    private static boolean serverIsUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 8090), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
