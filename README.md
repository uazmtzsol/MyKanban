# Personal Kanban (Java 21 · JavaFX · SQLite)

An offline personal kanban board — a Java rebuild of
[nishantpainter/personal-kanban](https://github.com/nishantpainter/personal-kanban).
Visualize your work, limit your work-in-progress (WIP), and move cards across
columns with drag & drop. All data stays in a local SQLite file; nothing ever
leaves your machine.

## Features

- **Multiple boards** — keep personal, work, and project boards independent;
  switch from the *Boards* menu, create/rename/delete, last board reopened on start
- **Portable database files** — create a new database or open an existing one
  (*Database* menu) anywhere: a USB folder, a Dropbox/OneDrive/Drive synced
  folder, a network share. The chosen file is remembered across launches, and
  undo history always lives in a `history/` folder next to it, so a database
  is one self-contained unit you can move between computers
- **JSON export/import** — back up or share any board as a portable `.json` file
- Columns & cards: add, edit, delete, delete-all-in-column, clear board
- **Card due dates** with an overdue badge, and **labels** (chips)
- **Markdown notes** — double-click a card to open a detail window with a
  live-split markdown editor and preview (GFM tables & strikethrough);
  the card front shows a lightweight styled summary. Storage stays plain
  text; the preview WebView runs with JavaScript disabled
- **Recent databases** — the *Database* menu lists the last 5 opened files
  for one-click switching between computers' setups
- Drag & drop: reorder columns, move/reorder cards across columns with a
  **visual insertion indicator** — drop into the exact slot between two cards
- WIP limits with a visible `count/limit` badge (red when full)
- 8-color palette for columns and cards
- Dark mode toggle (persisted)
- 8 languages: EN, FR, ES, DE, RU, HI, ZH, JA (persisted, applied instantly)
- **Undo / redo** (`Ctrl+Z` / `Ctrl+Shift+Z`) — persistent, survives restarts
- Persistence: SQLite (WAL, foreign keys) with versioned schema migrations

## Requirements

- A JDK/JRE 21+ to run (tested with Amazon Corretto 21)
- Maven 3.9+ to build (IntelliJ's bundled Maven also works)

## Run (development)

```bash
mvn javafx:run
```

## Build the portable jar

```bash
mvn -Pportable package -DskipTests
```

This produces `target/personal-kanban.jar`: a fat jar bundling JavaFX and
native libraries for **Windows, Linux and macOS**, plus SQLite natives. One
jar runs on any OS with a plain Java 21+ — no JavaFX installation needed.

## Portable USB layout

Copy these next to each other on the USB drive (or any folder):

```
personal-kanban.jar   # from target/, built with the portable profile
kanban.bat            # Windows launcher (double-click)
kanban.sh             # Unix/Linux/macOS launcher (./kanban.sh)
```

Both launchers:

- use `jre/bin/java` **next to them if present** — drop a full JDK/JRE 21 into
  a `jre/` folder for a fully self-contained stick; otherwise they fall back
  to `java` on `PATH`
- keep **all data inside `data/` on the same drive** (database, undo history,
  settings) via `-Dpk.data.dir` — nothing is written to the host machine, so
  the stick is truly portable between computers

```bash
chmod +x kanban.sh    # first time on Unix/Linux/macOS
./kanban.sh
```

Without the launchers you can also run directly:

```bash
java -Dpk.data.dir=./data -jar personal-kanban.jar
```

## Data locations & transporting your data

| Mode | Location |
|---|---|
| Default | `~/.personalkanban/` (`kanban.db`, `history/*.json`) |
| Portable (launchers) | `<drive>/data/` next to the jar |
| User-chosen | Anywhere you pick in *Database → New/Open database* |

Use **Database → New database...** to start a fresh file (name and folder of
your choice) or **Database → Open database...** to work on an existing one —
for example a `.db` file living in a synced cloud folder or on a USB stick.
The menu shows the file in use, and the window title carries its name. The
choice is remembered (machine-local `config.properties`), so each computer
reopens its last database — and since one database file plus its `history/`
folder is the whole unit, moving or syncing that folder moves everything.

> Tip for cloud use: close the app before switching computers so SQLite
> finishes writing; the WAL journal checkpoints on close.

Existing single-board databases from earlier versions are migrated
automatically (schema versions V1→V4); your columns and cards become the
board **"My Board"**.

## Test

```bash
mvn test
```

The suite covers domain invariants (WIP, ordering, move semantics), service
use cases (multi-board catalog, export/import round-trips, per-board undo),
SQLite round-trips on temp databases, migration idempotency, and
[ArchUnit](https://www.archunit.org/) rules that keep the architecture honest:

- `domain` never imports JavaFX, JDBC, or any outer layer
- `application` never imports JavaFX, JDBC, or `ui`
- `ui` never imports JDBC, SQLite, or `infrastructure`

## Architecture

```
ui (JavaFX)          →  application (use cases, ports)  →  domain (entities, invariants)
                                                        →  infrastructure (SQLite/JSON adapters)
```

Dependencies point inward only. The composition root (`AppContext`) wires
concrete classes at startup; everything else depends on interfaces and the
domain.

| Layer | Contents |
|---|---|
| `domain.board` | `Board` (aggregate root, one per `BoardId`), `BoardColumn`, `Card` (due date, labels), value objects, immutable snapshot DTOs, domain events |
| `application` | `BoardService` facade (multi-board, undo, JSON export/import), `BoardCommand` objects, ports (`BoardRepository`, `SettingsStore`, `UndoHistory`) |
| `infrastructure` | SQLite adapters (`Database`, `SchemaMigrator`, `SqliteBoardRepository`, `SqliteSettingsStore`) and `JsonUndoHistory` |
| `ui` | `BoardController`, view builders, `Dialogs`, `ThemeManager`, `I18n` |

### Patterns & principles applied

- **Repository + Adapter** — `BoardRepository`/`SettingsStore`/`UndoHistory` ports, SQLite/JSON adapters (DIP)
- **Memento** — `BoardMemento` powers undo/redo, doubles as persistence DTO and as the export payload
- **Command** — every mutation is a `BoardCommand` object executed by the facade
- **Facade** — `BoardService` is the UI's single entry point
- **Value Objects** — `WipLimit`, typed IDs, `BoardColor` enum (no primitive obsession)
- **Rich domain model** — WIP and ordering invariants live in `Board`/`BoardColumn`, never in controllers
- **GRASP** — Information Expert, Creator, Pure Fabrication (`SchemaMigrator`, view builders), Low Coupling / High Cohesion
- **Anti-patterns avoided** — anemic models, God classes, static singletons, stringly-typed colors, business logic in the UI, raw JDBC outside infrastructure

## Project layout

```
src/main/java/com/personalkanban/
├── AppContext.java    # composition root
├── Main.java          # JavaFX bootstrap · Launcher.java (fat-jar entry)
├── domain/            # pure Java: entities, value objects, events
├── application/       # use cases, commands, ports
├── infrastructure/    # SQLite + JSON undo history adapters
└── ui/                # JavaFX controllers, builders, theme, i18n
kanban.bat / kanban.sh # portable launchers
```
