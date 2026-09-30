# Arquitectura y mapa de código

*Última actualización: 2026-09-30 (Sesión 4: notas, checklist, procesos,
precedencias, menú Archivo, atajos+F1, fondo personalizable, icono de app;
migraciones V5–V7; 161/161 tests)*

Referencia para saber **dónde** va cada cambio sin re-explorar. Rutas relativas
a `src/main/java/com/personalkanban/`.

## 1. Capas y reglas

```
ui (JavaFX)  →  application (casos de uso + puertos)  →  domain (entidades e invariantes)
                          ↑
infrastructure (adaptadores SQLite/JSON, implementa puertos)
```

- **domain**: Java puro, sin JavaFX/JDBC. Entidades mutables controladas por el
  agregado; value objects inmutables; eventos de dominio.
- **application**: casos de uso como `BoardCommand` objects ejecutados por la
  fachada `BoardService`; puertos = interfaces (`BoardRepository`,
  `SettingsStore`, `UndoHistory`).
- **infrastructure**: adaptadores concretos de los puertos (SQLite, JSON).
- **ui**: JavaFX. Los controladores **no** tocan JDBC ni SQL nunca; delegan en
  `BoardService`.

`ArchitectureTest` (ArchUnit) enforcea las reglas de import — cualquier clase
nueva debe respetarlas. La lógica de negocio nueva (parseo, política de
sugerencias, modos de vista) va en domain/application como clases puras
testeables; la UI solo pinta.

## 2. Clases clave por capa (mapa rápido)

### domain/board/
| Clase | Rol | Notas |
|---|---|---|
| `Board` | **Agregado raíz**. Columnas + tarjetas, invariantes, emite `DomainEvent`s | `addCard/editCard/moveCard/moveCardToSlot/removeCard` + bulk: `addLabels/removeLabels/recolorCards/removeCards/moveCardsToColumn` (atómicos, ver §3.10 de roadmap), `restore(BoardMemento)` |
| `BoardColumn` | Columna con WIP; dueña de sus tarjetas y su orden | `moveCardFrom` valida WIP del destino |
| `Card` | Inmutable en identidad, mutable en campos vía `Board`. Etiquetas: `MAX_LABELS=8`, `MAX_LABEL_LENGTH=40`. Sesión 4: `notes` (texto plano), `checklist` (lista de `ChecklistItem`), `processId` | `setLabels` lanza `IllegalArgumentException` si excede |
| `ChecklistItem` | **Record inmutable** `(id, text, done)` — ítem del checklist plano de la tarjeta (1 nivel por decisión del usuario); `withText`/`withDone` devuelven copias; el id estable permite convertirlo en tarjeta. `MAX_TEXT_LENGTH=120`, tope 50/tarjeta | Jackson lo serializa sin anotaciones |
| `Process` / `ProcessId` | Proceso = grupo con nombre de tarjetas relacionadas para un fin (sesión 4.6); las tarjetas lo referencian por id (no es contenedor) | `NotFoundException(ProcessId)` |
| `CardLink` | Record `(from, to)`: un enlace de precedencia tal como viaja en snapshots ("from precede a to") | lista plana, no mapa, por Jackson |
| `DependencyGuard` | Política pura de precedencias: `requireLinkable` rechaza auto-enlaces, desconocidos y **ciclos** (directos y transitivos, DFS); `topologicalOrder` = orden sugerido con `Result(ordered, cycleRemaining)` | 9 tests unitarios puros |
| `BoardColor` | Record `(name, hex)`. 8 presets + custom. `fromHex` de custom crea `name=null` (usar `stored()` para persistir: nunca null). `fromStored` lee hex o nombre legacy | Validación hex `#rrggbb` |
| `LabelFilter` | Filtro por etiquetas, **case-insensitive** (normaliza con `toLowerCase`), modos ALL/ANY, vacío = sin filtro | usado por `ColumnViewBuilder` |
| `LabelSuggester` | Lógica pura del autocompletado: `currentToken(text,caret)`, `suggestions(token,exclude)` por prefijo case-insensitive, `apply(...)` reemplaza token + espacio. Vocabulario = LinkedHashSet | test unitario puro; lo usa `LabelAutoComplete` (ui) |
| `BoardMemento` | Snapshot inmutable del tablero: `(columns, processes, links)` — procesos y precedencias incluidos desde la sesión 4, así undo/redo y export los cubren | Triple uso: undo/redo, persistencia, export JSON |
| `CardSnapshot` / `ColumnSnapshot` | DTOs inmutables del memento | con `toCard()`/`toColumn()` |
| `WipLimit` | Value object: unlimited o 0..999 | |
| IDs (`BoardId`, `ColumnId`, `CardId`) | Wrappers tipados de UUID | `Ids.newCardId()` etc. |
| Eventos (`CardAdded`, `CardMoved`, `CardRemoved`, `DomainEvent`) | Registro de lo ocurrido; se drenan tras cada transacción | hoy solo se drenan, no se consumen |

### application/
| Clase | Rol |
|---|---|
| `BoardService` | **Fachada única para la UI**. Catálogo multi-tablero, `execute(command)` con memento: captura *before* → ejecuta comando → `history.push(before)` → `persist()` → `drainEvents()`. También undo/redo, export/import JSON (Jackson), `selectInitialBoard` al arrancar, preferencias UI por tablero (`collapsedColumnsOf/setCollapsedColumns`, `cardViewSettingsOf/setCardViewSettings`) y vocabulario de etiquetas (`labelVocabulary`) |
| `command/*Command` | Un comando por mutación (`AddCardCommand`, `EditCardCommand`, `MoveCardToSlotCommand`…) — patrón Command. Sesión 4: `NoteCardCommand`, `Add/Toggle/Rename/Remove/ConvertChecklistItemCommand`, `Add/Rename/RemoveProcessCommand`, `AssignProcessCommand`, `LinkCardsCommand`, `UnlinkCardsCommand` |
| `port/BoardRepository` | `listBoards/createBoard/renameBoard/deleteBoard/load/save` |
| `port/SettingsStore` | `get/put` clave→valor (tabla `app_setting`) — usar para preferencias nuevas (modos de vista P2, etc.) sin migraciones |
| `port/UndoHistory` | `push/pop/clear/depth` por tablero |

⚠️ **Orden de la transacción (post-P0):** `finishTransaction` = `persist()`
primero y, solo si tiene éxito, `history.push(before)`; si persist falla,
restaura el agregado con el memento before (rollback en memoria). `undo` =
peek→persist→pop (entrada reintetable si falla). El error sube a la UI con
la causa raíz visible (`Dialogs.describeFailure`).

### infrastructure/sqlite/
| Clase | Rol | Notas |
|---|---|---|
| `Database` | Dueño de LA única conexión JDBC. WAL + FK on. `inMemory()` para tests | driver xerial cargado en static-init |
| `SchemaMigrator` | Migraciones versionadas `V<n>__desc.sql` de classpath, **lista explícita** en `REGISTERED_SCRIPTS` (añadir ahí las nuevas) | tabla `schema_version` |
| `SqliteBoardRepository` | Implementa el puerto. `save()` = transacción: `deleteBoardContents` (borra tarjetas+columnas del tablero) + reinserta TODO (patrón **replaceAll**) | ⚠️ ver "escalabilidad" en decisiones |
| `SqliteSettingsStore` | `app_setting` key/value | Preferencias no-dominio viven aquí: `ui.language`, `ui.theme`, `board.last`, `ui.columnstate.<boardId>` (columnas colapsadas, ids con `;`) |
| `DataAccessException` | RuntimeException envolvente de SQLException | mensaje "Could not save board <id>" |

### infrastructure/history/
- `JsonUndoHistory`: pila de mementos por tablero en `history/*.json` junto a
  la BD. Capacidad acotada por el adaptador.

### application/command/ (comandos, uno por mutación)
`AddColumnCommand, RenameColumnCommand, EditColumnCommand, MoveColumnCommand,
RemoveColumnCommand, AddCardCommand (expone createdCardId),
EditCardCommand, MoveCardCommand, MoveCardToSlotCommand, RemoveCardCommand,
ClearColumnCommand` + **bulk (P1.5):**
`AddLabelsCommand, RemoveLabelsCommand, RecolorCardsCommand,
RemoveCardsCommand, MoveCardsCommand`. El servicio captura memento
before/after: cada comando bulk = 1 entrada de undo.

### ui/
| Clase | Rol | Notas |
|---|---|---|
| `BoardController` | **Controlador raíz**. Construye toolbar/menús/filtro/columnas. Todos los intents `onXxx` (`onAddCard`, `onEditCard`, `onMoveCard`…). `guarded(Runnable)` convierte excepciones en diálogo de error. `rebuildAll()` reconstruye todo el grafo al cambiar idioma/BD/tablero; `refresh()` reconstruye solo columnas. Estado de **selección múltiple**: `selectionMode/selectionColumn/selectedCards` + intents `onToggleSelectionMode`, `onToggleCardSelection`, `isCardSelected` y `onBulk*` | los builders le llaman de vuelta (callbacks) |
| `ColumnViewBuilder` | Stateless: header `[＋ añadir] [☐ selección] [« colapsar] [⋯ menú]` (el ⋯ incluye editar, colapsar, eliminar columna y "Eliminar todas las tarjetas" — se quitó el botón directo por peligroso), WIP, tarjetas filtradas, slots de drop, drag sources. `buildCollapsed`: tira vertical 52px, título rotado -90°, contador, menú mínimo. `buildAll` recibe `collapsedIds` y `dark` | colapso persistido por tablero vía `BoardService` |
| `CardViewBuilder` | Vista de tarjeta: título + flags ★/! (gris/vivo), descripción SEGÚN modo efectivo (`addDescriptionByMode`: nada/3 líneas/completo), badge de vencida, chips FlowPane con color determinista, botones editar/eliminar, doble clic → detalle, clic derecho → menú contextual de modo de vista (override individual + "usar el del tablero"). En modo selección: sin botones/flags, borde punteado, clic alterna selección | |
| `CardDetailWindow` | Ventana no-modal con **3 pestañas** (sesión 4): Descripción (editor markdown + preview WebView JS off + botón ?), Notas (TextArea plano + Guardar notas) y Lista de tareas (añadir, marcar, y menú contextual del ítem: renombrar / **convertir en tarjeta** / eliminar). Botonera: ? | ⧉ | OK | Cancel. `open()` estático |
| `Dialogs` | Todos los diálogos modales: card/column form, confirm, info, error, prompts (2 aridades), bulk (etiquetas ±, color, mover), elección de plantilla (`newBoardColumnsDialog` → enum `NewBoardColumns{STANDARD,CUSTOM,EMPTY}`) y **parseo puro**: `parseLabels` (espacios O comas), `parseNewBoardColumns` (número ⇒ "Columna N" localizado, o nombres por comas; null = inválido, MAX=12), `describeFailure` | `CardForm`, `ColumnForm`, `BulkLabelsForm` records |
| `I18n` | Wrapper de ResourceBundle UTF-8. `text(key)` **devuelve la clave si no existe** — así se manifiestan las claves faltantes | bundles en `resources/i18n/` |
| `ThemeManager` | LIGHT/DARK → ruta de CSS | |
| `markdown/Markdown` | commonmark → HTML estilizado para el WebView (template con colores por tema). `toStyledDocument(md,…)`, `styledPage(html,…)` y `cheatsheetDocument(…)` (tabla "Escribe→Resultado" de la guía rápida; la columna render usa el MISMO motor para no divergir). ⚠️ NUNCA añadir `<noscript>` a la plantilla: con JS deshabilitado el WebView lo RENDERIZA visible (WebKit) — fue un bug visto en pantalla | |
| `markdown/MarkdownSummary` | AST → `Text` nodes nativos para el frente de tarjeta | `render(md, maxLines)` — P2 parametriza maxLines |
| `LabelAutoComplete` | Popup de sugerencias (ListView) sobre un TextField: ↑↓ navegan, Enter/Tab aplican, Esc cierra, clic aplica; debounce 120ms; `vocabularySupplier` dinámico para el filtro; nunca bloquea escribir | `attach(field, suggester, excludeSupplier)`; usa `LabelSuggester` |
| `ColorCss` | Puente BoardColor→CSS: `styleClass()` = `pk-color-<name|custom>`; `applySurface(region,color,dark)` = tinte de FONDO (12% claro/22% oscuro mezclado sobre superficie) + borde exacto para customs — reemplaza al deprecado `applyAccent`; `backgroundTint` devuelve "" para el color DEFAULT (sin tinte) | |
| `UndoRedoController` | Aceleradores Ctrl+Z/Ctrl+Shift+Z + propiedades canUndo/canRedo | |
| `pdf/BoardPdfExporter` | Exporta un `Node` (el `columnsRow` del tablero visible) a PDF: snapshot de píxeles vía `Node.snapshot`, convertido a `BufferedImage` a mano (sin `javafx-swing`), paginado tipo póster con Apache PDFBox a 150 DPI (páginas A4 landscape, pie de página "R/C" cuando hay más de una) | `export(Node, Path)`; sin dependencia de dominio/infraestructura |
| `AppIcons` | Carga `/icons/app-{16,32,48}.png` para `stage.getIcons()` (icono de barra de tareas; sesión 4) | recursos cosméticos: nunca bloquean el arranque |
| `ShortcutsHelpWindow` | Ayuda no-modal de atajos (F1 o menú Ayuda): tabla completa de atajos fijos; una instancia compartida, reabrir = traer al frente | sesión 4 |
| `PreferencesDialog` | Preferencias (menú Archivo): imagen de fondo + atenuado 0–0.8 + activar/desactivar; resultado `BackgroundChoice(path, dim)` | sesión 4 |
| `AppContext` (raíz) | Composition root: crea/intercambia Database+servicios al abrir BD; recents en `config.properties` | |

## 3. Esquema SQLite (V1..V7, al día)

```
board            (id TEXT PK, name TEXT NOT NULL, created_at INTEGER)
board_column     (id TEXT PK, board_id TEXT NOT NULL FK→board ON DELETE CASCADE,
                  title, description NOT NULL DEFAULT '',
                  color TEXT NOT NULL, position INTEGER NOT NULL,
                  wip_limit INTEGER NULL, created_at INTEGER)
card             (id TEXT PK, column_id TEXT NOT NULL FK→board_column ON DELETE CASCADE,
                  title, description NOT NULL DEFAULT '',
                  color TEXT NOT NULL, position INTEGER NOT NULL,
                  due_date TEXT NULL,               -- ISO-8601 o NULL
                  labels TEXT NOT NULL DEFAULT '',  -- separadas por \u001F
                  created_at INTEGER,
                  notes TEXT NOT NULL DEFAULT '',   -- V5: notas en texto plano
                  process_id TEXT NULL FK→process ON DELETE SET NULL)  -- V7
card_checklist_item (id TEXT PK, card_id FK→card ON DELETE CASCADE,
                  position INTEGER NOT NULL, text TEXT NOT NULL,
                  done INTEGER NOT NULL DEFAULT 0)                     -- V6
card_link        (from_card_id FK→card ON DELETE CASCADE,
                  to_card_id FK→card ON DELETE CASCADE,
                  PRIMARY KEY (from_card_id, to_card_id))              -- V7
process          (id TEXT PK, board_id FK→board ON DELETE CASCADE,
                  name TEXT NOT NULL, position INTEGER NOT NULL,
                  created_at INTEGER NOT NULL)                         -- V7
app_setting      (key TEXT PK, value TEXT)   -- V2: idioma, tema, board.last, ui.*
schema_version   (version INTEGER PK, description, applied_at)
```

Índices: `idx_column_board_position`, `idx_checklist_card_position`,
`idx_card_link_to`, `idx_process_board_position`.

Convenciones: colores guardados como **nombre de paleta** (legacy) o **hex**;
`BoardColor.fromStored` acepta ambos. IDs UUID string. Positions enteros
reescritos por orden de lista en cada save.

**Orden de inserción en `replaceAll` (importante):** procesos → columnas →
tarjetas → checklist → enlaces. SQLite valida las FK **inmediatamente**
aunque haya transacción: las tarjetas con `process_id` deben insertarse
DESPUÉS de sus procesos.

**Para migrar en el futuro** (p. ej. P3 tabla label): crear
`src/main/resources/db/migration/V8__<desc>.sql` y **registrar la ruta en
`SchemaMigrator.REGISTERED_SCRIPTS`** (no hay classpath scanning).

## 4. Flujos importantes

### Guardar (cada mutación)
`UI intent → BoardController.onXxx → BoardService.execute(cmd)`:
1. `before = BoardMemento.capture(board)` (snapshot completo)
2. `cmd.execute(board)` — muta el agregado, emite eventos
3. `history.push(boardId, before)` — undo persistente
4. `persist()` → `repository.save(id, capture(actual))` — **replaceAll transaccional**
5. `board.drainEvents()`

La UI se refresca con `refresh()` del controlador (reconstruye las columnas).

### Undo/redo
- `undo()`: `history.pop` → push del estado actual al redoStack (en memoria) → restore.
- `redo()`: inverso. El redoStack se vacía al abrir otro tablero o tras nueva mutación.
- Historial en disco (JSON): sobrevive reinicios.### Arranque

`Main.start → AppContext.create()` (lee config.properties; default
`~/.personalkanban/kanban.db`) → migraciones (V1..V7) → `BoardService` (abre
`board.last` o crea "My Board") → `BoardController` → escena 1200x720 con CSS
del tema e **iconos de app** (`stage.getIcons` desde `/icons`). El título de
la ventana y el badge `.board-name` de la toolbar los mantiene
`BoardController.updateBoardIdentity()` (tablero activo + archivo).

### Nuevo tablero (con plantilla)
`onNewBoard`: nombre → ChoiceDialog de plantilla (`STANDARD` preseleccionada:
Por hacer/Haciendo/Hecho localizados; `CUSTOM`: número o nombres por comas
vía `parseNewBoardColumns`, inválido NO crea nada; `EMPTY`) → createBoard +
addColumn por cada título → `rebuildAll()` + confirmación.

### Drag & drop de tarjetas
Cada tarjeta va seguida de un `Region` slot invisible (`drop-slot`, 8px). Al
arrastrar una tarjeta, el slot bajo el cursor se pinta (`drop-slot-active`).
Drop en slot N = `moveCardToSlot(cardId, columnId, N)` — semántica pre-drop:
cae exactamente donde estaba la línea, incluso reordenando dentro de la misma
columna. El área de tarjetas completa es drop-target fallback (append al final).

## 5. i18n y temas

- 4 bundles: `messages.properties` (en, base) + `_es, _de, _fr`.
  UTF-8 vía `I18n.UTF8Control`.
- **Toda** cadena visible pasa por `i18n.text("clave")`. Claves nuevas →
  añadir a los 4 archivos (lo enforcea `I18nCoverageTest`; la sesión 4 añadió
  ~60 claves: `menu.file`, `file.exit`, `prefs.*`, `help.*`, `checklist.*`,
  `process.*`, `filter.process.*`, `card.tab.*`, `card.notes.*`, `card.relations.tip`).
  Clave faltante = se muestra la clave (bug de los botones del editor markdown, P0-2).
- Cambiar idioma/tema/tablero/BD ⇒ `rebuildAll()` (grafo completo nuevo).
- CSS: una sola hoja activa (light/dark). Clases de tarjetas: `.card`,
  `.card-title`, `.card-description`, `.card-due`, `.card-due-overdue`,
  `.card-label-chip`, `.column`, `.column-title`,
  `.column-wip`, `.wip-full`, `.tool-button`, `.drop-slot(-active)`,
  `.detail-*`, `.md-editor`, `.pk-color-<name>`. Sesión 4: `.card-checklist`,
  `.card-checklist-done`, `.card-notes-indicator`, `.card-process-chip`,
  `.card-relations`, `.shortcut-keys/action`, `.prefs-file-label`.

## 6. Tests (dónde añadir)

| Área | Archivo |
|---|---|
| Dominio | `src/test/.../domain/board/BoardTest.java`, `LabelFilterTest`, `BoardColorTest`, `WipLimitTest`, `MoveCardToSlotTest`, `BulkCardOperationsTest` |
| Servicio | `application/BoardServiceTest.java` (con `port/InMemory*` fakes) |
| SQLite | `infrastructure/sqlite/SqliteBoardRepositoryTest.java` (BD temp real) |
| Contexto | `AppContextDatabaseManagementTest`, `AppContextRecentsTest` |
| i18n | `ui/I18nCoverageTest.java` (claves usadas en ui ⊆ los 4 bundles) + `ui/DialogsParseLabelsTest` (5) + `ui/ParseNewBoardColumnsTest` (7) |
| Sesión 4 | `DependencyGuardTest` (9, política pura), `CardExtrasTest` (12, dominio), `SessionFourServiceTest` (8, undo/preferencias), `SqliteSessionFourRoundTripTest` (2, SQLite real) |
| Arquitectura | `architecture/ArchitectureTest.java` |

Estilo: JUnit 5 + AssertJ (`assertThat`). Los tests no abren JavaFX (no hay
TestFX); la capa `ui` se prueba manualmente (checklists en PLAN.md).
