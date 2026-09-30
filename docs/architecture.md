# Arquitectura y mapa de código

*Última actualización: 2026-09-29 (post Sesión 2 completa: P0 + i18n×4 + P1.5 selección múltiple + plantillas + badge de tablero)*

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
| `Card` | Inmutable en identidad, mutable en campos vía `Board`. Etiquetas: `MAX_LABELS=8`, `MAX_LABEL_LENGTH=40`; strip + dedup **case-sensitive** hoy (⚠️ el filtro sí es case-insensitive — asimetría a arreglar en P1) | `setLabels` lanza `IllegalArgumentException` si excede |
| `BoardColor` | Record `(name, hex)`. 8 presets + custom. `fromHex` de custom crea `name=null` (usar `stored()` para persistir: nunca null). `fromStored` lee hex o nombre legacy | Validación hex `#rrggbb` |
| `LabelFilter` | Filtro por etiquetas, **case-insensitive** (normaliza con `toLowerCase`), modos ALL/ANY, vacío = sin filtro | usado por `ColumnViewBuilder` |
| `LabelSuggester` | Lógica pura del autocompletado: `currentToken(text,caret)`, `suggestions(token,exclude)` por prefijo case-insensitive, `apply(...)` reemplaza token + espacio. Vocabulario = LinkedHashSet | test unitario puro; lo usa `LabelAutoComplete` (ui) |
| `BoardMemento` | Snapshot inmutable del tablero (columnas + tarjetas) | Triple uso: undo/redo, persistencia, export JSON |
| `CardSnapshot` / `ColumnSnapshot` | DTOs inmutables del memento | con `toCard()`/`toColumn()` |
| `WipLimit` | Value object: unlimited o 0..999 | |
| IDs (`BoardId`, `ColumnId`, `CardId`) | Wrappers tipados de UUID | `Ids.newCardId()` etc. |
| Eventos (`CardAdded`, `CardMoved`, `CardRemoved`, `DomainEvent`) | Registro de lo ocurrido; se drenan tras cada transacción | hoy solo se drenan, no se consumen |

### application/
| Clase | Rol |
|---|---|
| `BoardService` | **Fachada única para la UI**. Catálogo multi-tablero, `execute(command)` con memento: captura *before* → ejecuta comando → `history.push(before)` → `persist()` → `drainEvents()`. También undo/redo, export/import JSON (Jackson), `selectInitialBoard` al arrancar, preferencias UI por tablero (`collapsedColumnsOf/setCollapsedColumns`, `cardViewSettingsOf/setCardViewSettings`) y vocabulario de etiquetas (`labelVocabulary`) |
| `command/*Command` | Un comando por mutación (`AddCardCommand`, `EditCardCommand`, `MoveCardToSlotCommand`…) — patrón Command |
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
| `CardDetailWindow` | Ventana no-modal editor markdown + preview WebView (JS off) + botón **?** que abre `cheatsheetDocument` en una segunda ventana (hija, resizable). Botonera: ? | OK | Cancel | `open()` estático |
| `Dialogs` | Todos los diálogos modales: card/column form, confirm, info, error, prompts (2 aridades), bulk (etiquetas ±, color, mover), elección de plantilla (`newBoardColumnsDialog` → enum `NewBoardColumns{STANDARD,CUSTOM,EMPTY}`) y **parseo puro**: `parseLabels` (espacios O comas), `parseNewBoardColumns` (número ⇒ "Columna N" localizado, o nombres por comas; null = inválido, MAX=12), `describeFailure` | `CardForm`, `ColumnForm`, `BulkLabelsForm` records |
| `I18n` | Wrapper de ResourceBundle UTF-8. `text(key)` **devuelve la clave si no existe** — así se manifiestan las claves faltantes | bundles en `resources/i18n/` |
| `ThemeManager` | LIGHT/DARK → ruta de CSS | |
| `markdown/Markdown` | commonmark → HTML estilizado para el WebView (template con colores por tema). `toStyledDocument(md,…)`, `styledPage(html,…)` y `cheatsheetDocument(…)` (tabla "Escribe→Resultado" de la guía rápida; la columna render usa el MISMO motor para no divergir). ⚠️ NUNCA añadir `<noscript>` a la plantilla: con JS deshabilitado el WebView lo RENDERIZA visible (WebKit) — fue un bug visto en pantalla | |
| `markdown/MarkdownSummary` | AST → `Text` nodes nativos para el frente de tarjeta | `render(md, maxLines)` — P2 parametriza maxLines |
| `LabelAutoComplete` | Popup de sugerencias (ListView) sobre un TextField: ↑↓ navegan, Enter/Tab aplican, Esc cierra, clic aplica; debounce 120ms; `vocabularySupplier` dinámico para el filtro; nunca bloquea escribir | `attach(field, suggester, excludeSupplier)`; usa `LabelSuggester` |
| `ColorCss` | Puente BoardColor→CSS: `styleClass()` = `pk-color-<name|custom>`; `applySurface(region,color,dark)` = tinte de FONDO (12% claro/22% oscuro mezclado sobre superficie) + borde exacto para customs — reemplaza al deprecado `applyAccent`; `backgroundTint` devuelve "" para el color DEFAULT (sin tinte) | |
| `UndoRedoController` | Aceleradores Ctrl+Z/Ctrl+Shift+Z + propiedades canUndo/canRedo | |
| `AppContext` (raíz) | Composition root: crea/intercambia Database+servicios al abrir BD; recents en `config.properties` | |

## 3. Esquema SQLite (V1..V4, al día)

```
board            (id TEXT PK, name TEXT NOT NULL, created_at INTEGER)
board_column     (id TEXT PK, board_id TEXT NOT NULL FK→board ON DELETE CASCADE,
                  title, description NOT NULL DEFAULT '',
                  color TEXT NOT NULL, position INTEGER NOT NULL,
                  wip_limit INTEGER NULL, created_at INTEGER)
card             (id TEXT PK, column_id TEXT NOT NULL FK→board_column ON DELETE CASCADE,
                  title, description NOT NULL DEFAULT '',
                  color TEXT NOT NULL, position INTEGER NOT NULL,
                  due_date TEXT NULL  -- ISO-8601 o NULL
                  labels TEXT NOT NULL DEFAULT ''  -- separadas por ';' hoy
                  created_at INTEGER)
app_setting      (key TEXT PK, value TEXT)   -- V2: idioma, tema, board.last
schema_version   (version INTEGER PK, description, applied_at)
```

Índices: `idx_column_board_position (board_id, position)`.

Convenciones: colores guardados como **nombre de paleta** (legacy) o **hex**;
`BoardColor.fromStored` acepta ambos. IDs UUID string. Positions enteros
reescritos por orden de lista en cada save.

**Para migrar en el futuro** (p. ej. P3 tabla label): crear
`src/main/resources/db/migration/V5__<desc>.sql` y **registrar la ruta en
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
`~/.personalkanban/kanban.db`) → migraciones → `BoardService` (abre
`board.last` o crea "My Board") → `BoardController` → escena 1200x720 con CSS
del tema. El título de la ventana y el badge `.board-name` de la toolbar los
mantiene `BoardController.updateBoardIdentity()` (tablero activo + archivo).

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

- 8 bundles: `messages.properties` (en, base) + `_es, _de, _fr, _ru, _hi, _zh, _ja`.
  UTF-8 vía `I18n.UTF8Control`.
- **Toda** cadena visible pasa por `i18n.text("clave")`. Claves nuevas →
  añadir a los 9 archivos. Clave faltante = se muestra la clave (bug de los
  botones del editor markdown, P0-2).
- Cambiar idioma/tema/tablero/BD ⇒ `rebuildAll()` (grafo completo nuevo).
- CSS: una sola hoja activa (light/dark). Clases de tarjetas: `.card`,
  `.card-title`, `.card-description`, `.card-due`, `.card-due-overdue`,
  `.card-label-chip` (⚠️ sin regla CSS aún), `.column`, `.column-title`,
  `.column-wip`, `.wip-full`, `.tool-button`, `.drop-slot(-active)`,
  `.detail-*`, `.md-editor`, `.pk-color-<name>` para columnas y tarjetas.

## 6. Tests (dónde añadir)

| Área | Archivo |
|---|---|
| Dominio | `src/test/.../domain/board/BoardTest.java`, `LabelFilterTest`, `BoardColorTest`, `WipLimitTest`, `MoveCardToSlotTest`, `BulkCardOperationsTest` |
| Servicio | `application/BoardServiceTest.java` (con `port/InMemory*` fakes) |
| SQLite | `infrastructure/sqlite/SqliteBoardRepositoryTest.java` (BD temp real) |
| Contexto | `AppContextDatabaseManagementTest`, `AppContextRecentsTest` |
| i18n | `ui/I18nCoverageTest.java` (claves usadas en ui ⊆ los 4 bundles) + `ui/DialogsParseLabelsTest` (5) + `ui/ParseNewBoardColumnsTest` (7) |
| Arquitectura | `architecture/ArchitectureTest.java` |

Estilo: JUnit 5 + AssertJ (`assertThat`). Los tests no abren JavaFX (no hay
TestFX); la capa `ui` se prueba manualmente (checklists en PLAN.md).
