# PLAN — Roadmap de trabajo por sesiones

> Documento **vivo**: se actualiza en cada sesión (estado, hallazgos nuevos,
> checklists). El contexto estable del proyecto vive en `docs/`
> (ver `docs/README.md`). El usuario elige qué fase ejecutar en cada sesión.

*Última actualización: 2026-09-30 · **Sesión 5 completa**: 5 refinamientos
sobre la Sesión 4 (modo oscuro legible, resaltes (i)/(u)/(iu), botón de
ordenamiento, sección avanzada plegable, botón de nota, soltar ARRIBA).
169/169 tests OK. Pendiente: prueba manual del usuario.*

---

## Sesión 5 — Refinamientos de UX (pedidos por el usuario)

- [x] **5.1 — Texto ilegible en modo oscuro.** La descripción de tarjeta se
  renderiza como nodos `Text` cuyo fill por defecto es NEGRO: sobre el
  fondo oscuro de la tarjeta no se leía. Fix: `.card-description .text
  { -fx-fill: … }` en ambos temas.
- [x] **5.2 — Resaltes de (i)/(u)/(iu).** Clases CSS nuevas en el frente
  de la tarjeta: `.card-priority-important` (borde ámbar),
  `.card-priority-urgent` (borde rojo) y `.card-priority-urgent-important`
  (la más fuerte: borde rojo-rosado de 2px + superficie cálida). Los tres
  se distinguen entre sí y del resto, en claro y oscuro.
- [x] **5.3 — Botón de ordenamiento por prioridad (↓★).** En el header de
  cada columna: ordena SOLO esa columna como (★+!) → (!) → (★); las demás
  tarjetas conservan su orden relativo (partición estable). 1 transacción
  deshacible (`SortColumnByPriorityCommand` + `Board.sortColumnByPriority`).
- [x] **5.4 — Sección avanzada plegable en el diálogo de tarjeta.** Botón
  ☑ "Opciones avanzadas": plegado muestra los campos mínimos (título,
  descripción, color, fecha, etiquetas); expandido añade Notas (texto),
  **Lista de tareas como líneas** (`"[x] tarea"` = hecha, `"[ ] tarea"` o
  texto = pendiente; una por línea) y Proceso (combo). Al editar una
  tarjeta los campos avanzados se precargan y SOLO se aplican si cambió
  algo (undo con significado). `Board.setChecklistFromLines` conserva la
  identidad de los ítems existentes (match por texto).
- [x] **5.5 — Botón de nota (📝) junto a ★/!.** Atenuado si la tarjeta no
  tiene notas; vivo si las tiene; siempre abre la ventana de detalle para
  ver/editarlas.
- [x] **5.6 — Soltar ARRIBA de una tarjeta.** Cada tarjeta es ahora una
  zona de drop completa: el ~40 % superior suelta ARRIBA (línea superior
  azul) y el resto suelta DEBAJO (línea inferior azul) — ya no es
  contra-intuitivo poner una tarjeta de primera en la columna. El slot
  tras la última tarjeta sigue sirviendo para "al final".
- [x] **Tests:** 8 nuevos (`SessionFiveDomainTest` 6, `SessionFiveServiceTest`
  2). Suite completa **169/169 OK** (incluye i18n: claves `column.sort.priority`,
  `card.notes.button.tip`, `card.advanced.toggle`, `card.process.*`,
  `checklist.dialog.*` en los 4 bundles).
- [x] **5.7 — Persistencia visible (pedido posterior).** Aclaración clave:
  cada cambio YA se confirma en SQLite al instante (persist-first); los
  archivos `kanban.db-wal`/`-shm` son el diario interno, parte de la BD.
  Añadido lo que faltaba: **checkpoint cada 5 min** (el diario se integra
  a `kanban.db` y se vacía), **cierre limpio** al salir (la X de la
  ventana, Archivo→Salir y Ctrl+Q cierran la conexión: SQLite integra el
  diario y ELIMINA los side-files), **Ctrl+S / botón 💾 / Archivo→Guardar
  ahora** como tranquilidad manual. Tests: `CheckpointTest` (2).
  Suite **171/171 OK**.

### Checklist de prueba manual (usuario)

1. **Modo oscuro:** cambiar a oscuro → la descripción de las tarjetas se
   lee (gris claro), en claro sigue bien.
2. **Resaltes:** poner (i) a una tarjeta, (u) a otra y (iu) a una tercera →
   las tres se ven distintas entre sí y del resto; probar en oscuro.
3. **Ordenamiento:** botón ↓★ del header de columna → (iu) arriba, luego
   (u), luego (i), el resto no se mueve. Ctrl+Z lo revierte.
4. **Diálogo avanzado:** + tarjeta → plegado muestra lo mínimo → clic en
   ☑ Opciones avanzadas → escribir notas, 3 líneas de checklist (una con
   `[x] `) y elegir proceso → OK → en la tarjeta aparecen 📝, ☑ y el chip
   de proceso. Editar la tarjeta → los campos avanzados están precargados.
5. **Botón 📝:** aparece junto a ★/!; en tarjeta sin notas va atenuado y
   con notas va vivo; clic abre el detalle.
6. **Soltar arriba:** arrastrar una tarjeta sobre la MITAD SUPERIOR de la
   primera tarjeta de una columna → línea azul ARRIBA → se queda de
   primera. Sobre la mitad inferior → línea abajo → cae detrás.
7. **Persistencia:** crear un cambio → la BD ya lo tiene (abrir el archivo
   con otro lector SQLite lo confirma) → cerrar la app → en la carpeta de
   datos SOLO queda `kanban.db` (sin `-wal`/`-shm`) y al reabrir todo
   está. Ctrl+S o 💾 en cualquier momento dan el mensaje "Todo está
   guardado" — los cambios ya estaban guardados: cada mutación confirma
   al instante en el diario de rollback.

---

## Sesión 4 — Las 8 características (pedidas por el usuario)

> Decisiones previas confirmadas con el usuario:
> ① WBS = **checklist por tarjeta** (plana, 1 nivel, ítems convertibles en
> tarjetas — no jerárquica por ahora) · ③ "Añadir notas" = **notas por
> tarjeta** en texto plano, separadas de la descripción markdown · ④
> **atajos fijos + ayuda F1** (no configurables por ahora).

- [x] **4.1 — Menú Archivo + Salir / saltos de línea / icono de app.**
  `menu.database` renombrado a **Archivo** (File/Datei/Fichier) + ítem
  **Salir** (Ctrl+Q, con confirmación de cierre implícita de JavaFX
  limpia). Saltos de línea visibles en la tarjeta: `MarkdownSummary` ya no
  convierte el salto suave en espacio. Icono propio (azul, 3 columnas
  kanban) en 16/32/48 px bajo `/icons`, registrado en `Main.start` —
  corrige la taza de Java en la barra de tareas de Windows.
- [x] **4.2 — Atajos de teclado fijos + ayuda.** Nuevos: **F1** ayuda,
  **Ctrl+N** nuevo tablero, **Ctrl+F** enfocar filtro, **Ctrl+D** tema
  oscuro, **Ctrl+Q** salir. Nueva ventana no-modal `ShortcutsHelpWindow`
  con la tabla completa de atajos (menú Ayuda o F1).
- [x] **4.3 — Preferencias: imagen de fondo.** Menú Archivo →
  **Preferencias**: elegir imagen (recuerda `io.lastdir`), atenuación
  0–80 % para legibilidad, casilla para activar/desactivar. Se pre-atenua
  la imagen UNA vez (BufferedImage, truco del exportador PDF) y se aplica
  como fondo COVER. Persistido en `app_setting` `ui.background`
  (`"ruta|atenuado"`); archivo inexistente = sin fondo y sin error.
- [x] **4.4 — Notas por tarjeta (migración V5).** `card.notes TEXT NOT NULL
  DEFAULT ''`. Pestañas en la ventana de detalle: **Descripción** (markdown,
  igual que antes), **Notas** (TextArea plano + botón Guardar notas) y
  **Lista de tareas**. Indicador 📝 en el frente con extracto en tooltip.
- [x] **4.5 — Checklist por tarjeta (migración V6).** Tabla
  `card_checklist_item (id, card_id, position, text, done)`; ítem =
  record inmutable con id estable. UI en la pestaña "Lista de tareas":
  añadir, marcar/desmarcar, renombrar/eliminar/**convertir en tarjeta**
  (menú contextual del ítem; la nueva tarjeta cae en la MISMA columna y el
  ítem sale del checklist). Progreso **☑ n/m** en el frente de la tarjeta.
  `ChecklistItem.MAX_TEXT_LENGTH=120`, tope 50 ítems/tarjeta.
- [x] **4.6 — Precedencias + Procesos + filtro (migración V7).** Tablas
  `card_link (from_card_id, to_card_id)` y `process (id, board_id, name,
  position)` + `card.process_id`. **Menú Procesos**: crear, renombrar,
  eliminar (sus tarjetas quedan sin proceso). **Asignación** en el diálogo
  de tarjeta (combo) + chip ⚬ nombre de proceso en el frente. **Filtro por
  proceso** en la barra de filtro (combina con el de etiquetas).
  **Precedencias**: en el diálogo de tarjeta, campos "Tareas previas" y
  "Tareas posteriores" (multi-selección con búsqueda); validación
  **anti-ciclos** (directos y transitivos, `DependencyGuard`); badges
  **←n →n** en el frente; botón **Orden sugerido** en el menú ⋯ de columna
  (orden topológico; si hay ciclo, aviso con las tarjetas atrapadas).
- [x] **Tests:** 31 nuevos (`DependencyGuardTest` 9, `CardExtrasTest` 12,
  `SessionFourServiceTest` 8, `SqliteSessionFourRoundTripTest` 2) — suite
  completa **161/161 OK** (incluye ArchitectureTest e I18nCoverageTest,
  que validó las ~60 claves nuevas en los 4 bundles).

### Checklist de prueba manual (usuario)

1. **Icono:** arrancar la app → icono azul con columnas en la barra de
   tareas y la ventana (ya no la taza de Java).
2. **Menú Archivo:** está "Archivo" (antes "Base de datos") con
   **Preferencias** arriba y **Salir** abajo (o Ctrl+Q).
3. **Saltos de línea:** editar una tarjeta con varias líneas → se ven en
   la tarjeta (no todo en un reglón).
4. **Atajos:** F1 abre la ayuda; Ctrl+N crea tablero; Ctrl+F enfoca el
   filtro; Ctrl+D cambia el tema; Ctrl+Q sale.
5. **Fondo:** Archivo → Preferencias → elegir una imagen → aparece de
   fondo atenuada; mover el atenuado; desmarcar → desaparece. Reiniciar →
   se conserva. Probar tema claro y oscuro.
6. **Notas:** doble clic en tarjeta → pestaña Notas → escribir → Guardar
   notas → aparece 📝 en la tarjeta (tooltip con extracto). Ctrl+Z la
   quita. Reiniciar → sigue ahí.
7. **Checklist:** pestaña Lista de tareas → añadir 3 ítems → marcar 1 →
   la tarjeta muestra "☑ 1/3". Clic derecho en un ítem → **Convertir en
   tarjeta** → nueva tarjeta en la misma columna con ese título y el ítem
   desaparece del checklist. Ctrl+Z deshace la conversión.
8. **Procesos:** menú Procesos → Nuevo proceso "Mudanza" → editar 2-3
   tarjetas y asignarles el proceso → chip ⚬ Mudanza en cada una → filtro
   "Proceso: Mudanza" muestra solo esas → **Orden sugerido** (menú ⋯ de
   columna) lista las tarjetas.
9. **Precedencias:** en el diálogo de tarjeta, "Tareas previas" de B = A
   (y de C = B) → la tarjeta muestra ←1 →1 según toque → intentar poner
   A con previa = C → **error de ciclo** (correcto) → Orden sugerido
   devuelve A, B, C.
10. **Undo/redo** sobre notas, checklist, procesos y enlaces: cada paso
    es una transacción deshacible.
11. (Regresión) Export JSON → importar en otra BD → notas/checklist/
    procesos/enlaces sobreviven. BDs viejas abren sin errores (migración
    automática V5→V7).

---

## Estado general

| Fase | Contenido | Estado |
|---|---|---|
| **P0–P2 + Sesión 3** | Correcciones, etiquetas, vistas, PDF, resize… | 🔄 Código hecho; faltan pruebas manuales pendientes del usuario |
| **Sesión 4 — 8 características** | Archivo+Salir · saltos de línea · icono · atajos+F1 · fondo personalizable · notas · checklist · procesos+precedencias | 🔄 Código hecho + 161/161 tests; falta prueba manual |
| **P3 — Deseable (futuro)** | Impresión · registro de etiquetas · clic en chip = filtrar | ⬜ Bloqueada hasta que el usuario la pida |

Leyenda: ⬜ pendiente · 🔄 en curso · ✅ hecha y probada por el usuario

---

## Punto técnico: orden de ejecución elegido (Sesión 4)

Quick wins sin migración primero (⑧⑤⑥ → ④ → ⑦), luego migraciones de
menor a mayor acoplamiento: V5 notas → V6 checklist → V7 procesos+enlaces.
Cada migración acompaña a su feature; la BD queda estable entre pruebas.
Migraciones: crear `V<n>__desc.sql` **y** registrar en
`SchemaMigrator.REGISTERED_SCRIPTS`.

---

## Fases anteriores (resumen; el detalle está en docs/ y en las notas de sesión)

- **Sesión 1:** diagnóstico con causas raíz; creada `docs/` y este PLAN.
- **Sesión 2 (P0 + i18n×4 + P1.5 + plantillas):** fix color custom,
  rollback persist-first, describeFailure, i18n EN/ES/DE/FR, selección
  múltiple + bulk, tablero con plantilla, badge de tablero.
- **Sesión 2d–2i (P1.6/P1/P2):** tinte de superficies, colapso de columnas,
  chips + autocompletado, flags ★/!, modos de vista ☳ Ctrl+1/2/3, guía
  markdown, carpeta recordada, drop en columna vacía, resize de columnas.
  Sello v2d-20260929-2111; 130/130 tests.
- **Sesión 3:** asa de resize en última columna, fuera "Vaciar tablero" de
  la toolbar, botón copiar-todo del editor, toggles ★/! en el filtro,
  exportar tablero a PDF (PDFBox, paginado póster). 130/130 tests.

---

## P3 — Deseable / futuro (NO implementar hasta que el usuario lo pida)

- **Impresión de tableros** (evaluar export HTML vs PrinterJob).
- **Registro de etiquetas** (migración propia): colores elegidos,
  autocompletado global, renombrado propagado.
- **Clic en chip = filtrar** por esa etiqueta.
- **Checklist jerárquica** (sub-niveles) si el usuario la pide — hoy es
  plana por decisión explícita.
- **Higiene menor:** `deleteBoard` parametrizado, import duplicado en
  `ColumnViewBuilder`, aviso por límite de etiquetas.

---

## Notas de sesión

### Sesión 4 — 2026-09-30 (las 8 características)

- **Dominio:** `Card` gana `notes`, checklist (lista de `ChecklistItem`
  record inmutable con id estable) y `processId`; `CardSnapshot` los
  transporta (memento = undo/redo y export gratis). `Board` gana procesos
  (`LinkedHashMap`), enlaces de precedencia (`LinkedHashMap<CardId,
  LinkedHashSet<CardId>>` + `CardLink` plano para snapshots) y
  `convertChecklistItemToCard`. `DependencyGuard` = política pura
  (anti-ciclos DFS + orden topológico con remainder de ciclo).
  `NotFoundException(ProcessId)` nueva; `CyclicDependencyException`
  extiende `DomainException`.
- **Jackson:** `ChecklistItem` y `CardLink` como records — serialización
  de memento/export sin anotaciones. Lección previa aplicada: nada de
  mapas con claves tipadas en el snapshot (las claves `CardId` se
  serializarían como `CardId[value=…]`).
- **Aplicación:** 12 comandos nuevos (`NoteCard`, `Add/Toggle/Rename/
  Remove/Convert ChecklistItem`, `Add/Rename/Remove Process`,
  `AssignProcess`, `Link/Unlink Cards`) + `AddCardCommand` extendido
  (notas/checklist/proceso opcionales) + casos de uso en `BoardService`
  (`setCardNotes`, `addChecklistItem`, `convertChecklistItemToCard`,
  `addProcess`, `assignCardToProcess`, `linkCards`, `suggestedOrder`,
  `background()/setBackground()`, `boardBackgroundOf/setBoardBackground`).
  Todo persist-first, undo en 1 paso.
- **Infraestructura:** V5 (notas), V6 (checklist), V7 (enlaces + procesos
  + `card.process_id` FK ON DELETE SET NULL). `replaceAll` ahora borra
  procesos del tablero y reinserta con **orden FK**: procesos → columnas →
  tarjetas → checklist → enlaces. `load` lee las 4 piezas nuevas.
  ✔️ Hallazgo: la FK de `card.process_id` falla si las tarjetas se
  insertan antes que los procesos aunque la escritura sea una transacción
  (SQLite valida inmediatamente) — el orden de batches importa.
- **UI:** menú **Archivo** (+Preferencias, +Salir), menú **Procesos**,
  menú **Ayuda** (atajos), 5 atajos nuevos, ventana `ShortcutsHelpWindow`,
  diálogo `PreferencesDialog` (imagen + atenuado, imagen pre-atenuada con
  BufferedImage + data-URI PNG), `AppIcons` (icono 16/32/48), filtro por
  proceso en la barra, `CardDetailWindow` con pestañas (Descripción /
  Notas / Checklist con conversión), frente de tarjeta con ☑ n/m, 📝,
  chip de proceso y ←n →n. ~60 claves i18n nuevas en EN/ES/DE/FR.
- **Icono de app:** PNGs generados con script desechable (BufferedImage,
  tile azul redondeado + 3 columnas kanban) en `src/main/resources/icons/`
  + `Main.start` los registra (`stage.getIcons`).
- **Tests:** 161/161 (31 nuevos). Errores pillados por los propios tests
  durante el desarrollo: SELECT sin la columna `notes` (round-trip),
  INSERT sin escribirla, orden FK procesos↔tarjetas, `restore` que
  reemplaza enlaces (el test debía incluir los existentes), y
  `background()` debía normalizar `""` → null.
- **Pendiente:** prueba manual del usuario (checklist arriba) y
  regeneración del portable con sello nuevo.

## Sesión 6 — procesos, time tracking, editor Markdown y plan de sync

- **Time tracking (completo):**
  - Dominio: `EntryId`, `TimelineEntry` (start/end/comentario, inmutables
    una vez cerrados; sólo borrado), `Timeline` (un registro por clic).
  - `Card.timeline()` ahora es un campo real (antes devolvía uno nuevo
    cada vez: bug corregido).
  - Persistencia: migración **V8** (`timeline`, epoch millis, FK
    ON DELETE CASCADE), `BoardMemento` con 4º campo `timeline`,
    `SqliteBoardRepository` reescrito (tenía duplicados que no
    compilaban), `JsonUndoHistory` con módulo Jackson propio para
    `TimelineEntry` (dominio sigue puro).
  - `BoardMemento.capture` copia defensivamente los `TimelineEntry`
    (mutables) para que un `stop` posterior no reescriba el "antes".
  - Servicio: `startTimeTracking` / `stopTimeTracking` /
    `commentRunningTimeEntry` / `removeTimeEntry` / `timeEntriesOf`
    (comandos `Start/Stop/Comment/RemoveTimeEntry`), todo undoable.
  - UI: pestaña **Tiempo** en `CardDetailWindow` con botón cronómetro,
    comentario y lista de registros (borrar, no editar). CSS + i18n
    (EN/ES/FR/DE).
- **Editor Markdown reutilizable:** `ui/MarkdownEditor.java` con toggle
  lápiz/ojo entre edición y vista renderizada; usado en descripción y
  notas del detalle y en el diálogo de tarjeta.
- **Vista de procesos:** `ui/ProcessViewBuilder.java` — una fila por
  proceso, tarjetas de izquierda a derecha unidas por flechas según el
  orden de precedencia; cada tarjeta con 2 flechas superiores (crear
  tarjeta nueva antes/después) y 2 inferiores (elegir tarjeta existente).
  Alternada con el kanban por el botón ⇄ de la barra. Se añadió
  `Board.hasLink(from,to)`.
- **Etiquetas manuales:** `Dialogs.parseLabels` ahora rechaza el '#' al
  inicio (vía `LabelConventions`); las etiquetas de proceso llevan '#'.
- **Sincronización (plan detallado, no implementada):** ver
  `docs/sync-design.md`. Enfoque Zotero: BD local (`kanban.db`) + outbox de
  cambios y lápidas, conflictos LWW por campo, claves estables
  (`CardId`/`EntryId`). Backends gratuitos comparados: Cloudflare D1,
  Turso, Supabase, Firestore y Google Drive (blob). Recomendación: empezar
  con Google Drive (JSON por tablero) y luego SQLite remoto (D1/Turso).
- **Tests:** 191/191 verdes (`mvn test -DskipITs`). Nuevos: `TimelineTest`,
  `LabelConventionsTest`, `SqliteTimelineRoundTripTest`,
  `SessionSixServiceTest`, caso '#' en `DialogsParseLabelsTest`, y
  export/import con timeline en `BoardServiceTest`.
- **Pendiente:** notas por flecha en la vista de procesos (hoy la flecha
  enlaza/desenlaza), implementación real del sync, y prueba manual de la
  vista de procesos y del cronómetro.

## Sesión 7 — WAL eliminado (pedido por el usuario)

- **Diario de rollback (DELETE) en vez de WAL:** el usuario pidió quitar
  el WAL — pocos cambios y datos pequeños, el fsync por commit es
  aceptable. `Database.configure()` ahora usa `PRAGMA journal_mode =
  DELETE`; ya no se crean archivos `kanban.db-wal`/`-shm`.
- **Eliminado:** `Database.checkpoint()`, el scheduler de checkpoint
  cada 5 min de `AppContext`, `checkpointQuietly()`/`saveCheckpoint()`
  y el apagado del scheduler en `close()`. `shutdown()` ahora solo
  cierra el contexto; Ctrl+S/💾 muestra el mensaje de confirmación
  (los cambios ya estaban guardados al instante).
- **Tests:** `CheckpointTest` reemplazado por `DurabilityTest` (2):
  sin sidecars `-wal`/`-shm` con la conexión abierta ni tras cerrar,
  y BD reabrible tras cierre limpio.
- **Benchmark de latitud (`WriteLatencyBenchmarkTest`):** guardar un
  tablero realista (4 columnas × 12 tarjetas con notas, etiquetas,
  checklist, enlace y entrada de tiempo) cuesta **p50 ≈ 5-12 ms,
  p95 ≈ 7-26 ms, máx ≈ 76 ms por commit** (aislado vs. suite
  completa en disco ocupado). Es el fsync por transacción:
  imperceptible para un humano (umbral ≈ 100 ms) con este
  volumen de datos, como preveía el usuario.

## Sesión 8 — Diálogo de configuración de la API + despliegue en XAMPP

- **Menú (Base de datos):** línea de estado de sync (URL del
  servidor o «sync off», desactivada) + entrada «Configuración
  de la sincronización» (`sync.config.title`) que abre el
  `SyncConfigDialog`.
- **`SyncConfigDialog`** — todo lo configurable en un sitio:
  URL del servidor, clave de API, botón **Probar conexión**
  y catálogo de tableros remotos (nombre, versión, actualizado,
  id). Las sondas HTTP corren en un hilo de fondo; OK guarda
  vía `AppContext.configureSync` (re-wira el repositorio al
  vuelo); «Limpiar» desactiva el sync. Nuevo seam de test
  `AppContext.syncRepositoryFor(url, key)`.
- **Despliegue local:** API copiada a `C:/xampp/htdocs/sync/`
  (Apache + MariaDB `personalkanban`); `config.php` con una
  clave de desarrollo generada — el `.bat` NO la sobreescribe.
- **`deploy-sync-api.bat`** (raíz, CRLF): copia `index.php`,
  `config.sample.php` y `setup.sql` a `C:\xampp\htdocs\sync\`,
  acepta una carpeta destino como argumento y crea `config.php`
  desde el sample solo si no existe.
- **Fix del cliente (`HttpSyncRepository`):** `normalize()`
  conserva un `/` final cuando la URL tiene ruta — sin él,
  Apache 301-redirige `/sync` a `/sync/` y el JDK no seguía la
  redirección (el E2E recibía HTML en vez de JSON); ahora el
  cliente también sigue redirecciones (`Redirect.NORMAL`).
  E2E contra Apache verificado: catálogo (13 boards), fetch,
  push desfasado → 409 con el remoto, push forzado → Ok,
  tablero nuevo → Ok.
- **Tests:** 204/204 verdes. Docs: `docs/sync-design.md`
  (adaptador, UI hecha, suite) y este PLAN.
