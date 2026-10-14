package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardReminder;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trip tests for the reminder moments (T23 · B10, schema V11). */
class SqliteReminderRoundTripTest {

    private static final LocalDate DUE = LocalDate.of(2026, 10, 23);

    @TempDir
    Path tempDir;

    private Database database;
    private SqliteBoardRepository repository;
    private BoardId boardId;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("test.db"));
        new SchemaMigrator(database).migrate();
        repository = new SqliteBoardRepository(database);
        boardId = repository.createBoard("Reminder Board").id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void momentsRoundTripIncludingTheAllDayAndAttendedOnes() {
        Instant created = Instant.parse("2026-10-14T10:00:00Z");
        CardId cardId = new CardId("card-1");
        List<CardReminder> reminders = List.of(
                new CardReminder("r-1", LocalDate.of(2026, 10, 15), null, false),
                new CardReminder("r-2", LocalDate.of(2026, 10, 16), LocalTime.of(9, 30), true),
                new CardReminder("r-3", DUE, LocalTime.of(18, 0), false));

        repository.save(boardId, new BoardMemento(List.of(column(cardId, created, reminders)),
                List.of(), List.of(), List.of()));

        var card = repository.load(boardId).toBoard().findCard(cardId).orElseThrow();

        assertThat(card.reminders()).hasSize(3);
        assertThat(card.reminders().get(0).id()).isEqualTo("r-1");
        assertThat(card.reminders().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(card.reminders().get(0).time()).as("all-day stays all-day").isNull();
        assertThat(card.reminders().get(0).acknowledged()).isFalse();

        assertThat(card.reminders().get(1).time()).isEqualTo(LocalTime.of(9, 30));
        assertThat(card.reminders().get(1).acknowledged())
                .as("an attended moment must not nag again after a restart").isTrue();

        assertThat(card.reminders().get(2).date()).isEqualTo(DUE);
        assertThat(card.pendingReminderCount()).isEqualTo(2);
    }

    /**
     * A row written outside the app (or by a version that did not know about
     * hours) is read as an all-day moment instead of failing the whole load.
     */
    @Test
    void aRowWithoutAnHourReadsAsAnAllDayMoment() throws Exception {
        Instant created = Instant.parse("2026-10-14T10:00:00Z");
        CardId cardId = new CardId("card-legacy");
        repository.save(boardId, new BoardMemento(
                List.of(column(cardId, created, List.of())), List.of(), List.of(), List.of()));

        try (var statement = database.connection().prepareStatement(
                "INSERT INTO card_reminder (id, card_id, on_date, on_time, acknowledged) "
                        + "VALUES (?, ?, ?, NULL, 0)")) {
            statement.setString(1, "r-legacy");
            statement.setString(2, cardId.value());
            statement.setString(3, DUE.toString());
            statement.executeUpdate();
        }

        var card = repository.load(boardId).toBoard().findCard(cardId).orElseThrow();
        assertThat(card.reminders()).hasSize(1);
        assertThat(card.reminders().get(0).time()).isNull();
        assertThat(card.reminders().get(0).dueFrom().toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void deletingTheBoardCascadesItsReminderRows() throws Exception {
        Instant created = Instant.parse("2026-10-14T10:00:00Z");
        CardId cardId = new CardId("card-1");
        repository.save(boardId, new BoardMemento(List.of(column(cardId, created,
                List.of(new CardReminder("r-1", DUE, null, false)))),
                List.of(), List.of(), List.of()));
        assertThat(reminderRowCount()).isEqualTo(1);

        repository.deleteBoard(boardId);

        assertThat(reminderRowCount()).isZero();
    }

    private static ColumnSnapshot column(CardId cardId, Instant created, List<CardReminder> reminders) {
        return new ColumnSnapshot(new ColumnId("col-1"), "To Do", "", BoardColor.BLUE,
                WipLimit.unlimited(), created,
                List.of(new CardSnapshot(cardId, "con recordatorios", "", BoardColor.PINK, DUE,
                        List.of(), created, "", List.of(), null, reminders)));
    }

    private int reminderRowCount() throws Exception {
        try (var statement = database.connection().createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM card_reminder")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
