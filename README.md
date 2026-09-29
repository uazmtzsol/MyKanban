# Personal Kanban (Java 21 · JavaFX · SQLite)

An offline personal kanban board — a Java rebuild of
[nishantpainter/personal-kanban](https://github.com/nishantpainter/personal-kanban).
Visualize your work, limit your work-in-progress (WIP), and move cards across
columns with drag & drop. All data stays in a local SQLite file; nothing ever
leaves your machine.

## Features

- Columns & cards: add, edit, delete, delete-all-in-column, clear board
- Drag & drop: reorder columns, move/reorder cards across columns
- WIP limits with a visible `count/limit` badge (red when full)
- 8-color palette for columns and cards
- Dark mode toggle (persisted)
- 8 languages: EN, FR, ES, DE, RU, HI, ZH, JA (persisted)
- Undo / redo (`Ctrl+Z` / `Ctrl+Shift+Z`, last 100 mutations)
- Persistence: SQLite (WAL, foreign keys) in `~/.personalkanban/kanban.db`

## Requirements

- JDK 21+ (tested with Amazon Corretto 21)
- Maven 3.9+ (IntelliJ's bundled Maven also works)

## Run

```bash
mvn javafx:run
```

## Test

```bash
mvn test
```

The test suite covers domain invariants (WIP, ordering, move semantics),
service use cases with an in-memory repository (undo/redo, persistence
triggers), SQLite round-trips on a temp file database, and
[ArchUnit](https://www.archunit.org/) rules that keep the architecture honest:

- `domain` never imports JavaFX, JDBC, or any outer layer
- `application` never imports JavaFX, JDBC, or `ui`
- `ui` never imports JDBC, SQLite, or `infrastructure`

## Architecture

```
ui (JavaFX)          →  application (use cases, ports)  →  domain (entities, invariants)
                                                        →  infrastructure (SQLite adapters)
```

Dependencies point inward only. The composition root (`ui.AppContext`) wires
concrete classes at startup; everything else depends on interfaces and the
domain.

| Layer | Contents |
|---|---|
| `domain.board` | `Board` (aggregate root), `BoardColumn`, `Card`, value objects (`WipLimit`, `ColumnId`, `CardId`, `BoardColor`), immutable snapshot DTOs, domain events |
| `application` | `BoardService` facade, `BoardCommand` objects, ports (`BoardRepository`, `SettingsStore`) |
| `infrastructure.sqlite` | `Database`, `SchemaMigrator`, `SqliteBoardRepository`, `SqliteSettingsStore` |
| `ui` | `BoardController`, view builders, `Dialogs`, `ThemeManager`, `I18n` |

### Patterns & principles applied

- **Repository + Adapter** — `BoardRepository`/`SettingsStore` ports, SQLite adapters (DIP)
- **Memento** — `BoardMemento` powers undo/redo and doubles as the persistence DTO
- **Command** — every mutation is a `BoardCommand` object executed by the facade
- **Facade** — `BoardService` is the UI's single entry point
- **Value Objects** — `WipLimit`, `Position`, typed IDs, `BoardColor` enum (no primitive obsession)
- **Rich domain model** — WIP and ordering invariants live in `Board`/`BoardColumn`, never in controllers
- **GRASP** — Information Expert (columns own their cards), Creator (entity factories), Pure Fabrication (`SchemaMigrator`, view builders), Low Coupling / High Cohesion (layering)
- **Anti-patterns avoided** — anemic models, God classes, static singletons, stringly-typed colors, business logic in the UI, raw JDBC outside infrastructure

### Schema

Versioned migrations (`V1__init.sql`, `V2__app_settings.sql`) are applied at
startup by a small hand-rolled migrator; applied versions are tracked in
`schema_version`.

## Project layout

```
src/main/java/com/personalkanban/
├── domain/            # pure Java: entities, value objects, events
├── application/       # use cases, commands, ports
├── infrastructure/    # SQLite adapters + migrations
└── ui/                # JavaFX controllers, builders, theme, i18n
src/main/resources/
├── db/migration/      # V1/V2 SQL scripts
├── css/               # light.css / dark.css
└── i18n/              # 8 message bundles
```
