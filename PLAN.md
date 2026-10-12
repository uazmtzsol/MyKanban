# PLAN — Roadmap de trabajo por sesiones

> Documento **vivo**: se actualiza en cada sesión (estado, hallazgos nuevos,
> checklists). El contexto estable del proyecto vive en `docs/`
> (ver `docs/README.md`). El usuario elige qué fase ejecutar en cada sesión.

*Sesión 14 (2026-10-12, **en curso**) · bloque de producto pedido por el usuario: **B1** informe de tiempo, **B2** búsqueda global (Ctrl+Shift+F), **B3** recordatorios de vencimiento, **B4** captura rápida con sintaxis, **B5** plantillas propias + purga de archivadas, **B7** registro de etiquetas con color (cierra B9) y **B8** subtareas jerárquicas. Plan detallado por mejora (alcance, diseño, archivos, i18n, tests, criterio de hecho) en [`docs/plan-sesion-14.md`](docs/plan-sesion-14.md); estado al final de este documento.*

*Última actualización: 2026-10-12 · **Sesión 13:** cierre de la Sesión 11
(S11-5) y las seis mejoras pedidas en bloque por el usuario: tarjeta «actual»
al hacer clic con el ratón, foco en el campo del diálogo de etiquetas, menú
**Ver** con atajos (Ctrl+Shift+1/2), tarjetas seleccionadas claramente
distinguibles, tooltips correctos (`bulk.process` + banderas ★/!) y descripción
de tarjeta legible en tema obscuro — **todo hecho**, con pruebas de regresión
nuevas (incluido un clic de ratón real sobre la UI); ver la sección «Sesión 13»
al final. Suite **395/395 OK** (1 skip: endpoint PHP sin servidor) y jar
portable regenerado.*

*Sesión 12 (2026-10-11) · **catálogo global de etiquetas con gestor (añadir/renombrar/quitar con barrido entre tableros + etiquetas reservadas), visor de copias de conflicto integrado en el menú (cierra 3.1), atajo Ctrl+L para etiquetas, botón 📦 archivar en la barra de herramientas y fix del bug de notas con markdown — **todo hecho**, una por commit con push; ver la sección «Sesión 12» al final. Suite **385/385 OK** (1 skip: endpoint PHP sin servidor).*

*Sesión 10 (2026-10-09): cuatro características nuevas pedidas de golpe por el usuario (navegación con teclado, resumen vs. extensa de la tarjeta, exportar tarjeta, limpieza/archivado) — **las 4 hechas**, una por commit con push; ver la sección «Sesión 10» al final. Suite **367/367 OK** (1 skip: endpoint PHP sin servidor).*

*Sesión 9 (2026-10-07): mejoras adicionales recomendadas documentadas en [`docs/adiciones-propuestas.md`](docs/adiciones-propuestas.md) (**sin implementar**, para revisar al probar); plan de trabajo formalizado en [`docs/plan-sesiones.md`](docs/plan-sesiones.md) con fases A-B-C-D y checklist para continuar si se interrumpe; suite **236/236 tests OK** (1 skipped: `PhpSyncApiEndpointTest`, salta si no hay servidor PHP). Mejoras ya hechas en Sesión 9: sync §4/§4.1 (`SyncMerge`, `SyncService`, copias de conflicto), `SyncMergePropertyTest` (9 propiedades), B6 (clic en chip = filtrar) + `LabelChipFilterTest`, D3 (CI `.github/workflows/ci.yml`), manual de usuario y mejoras propuestas. Corregido un **NPE latente** del merge (columna huérfana). Pendiente: planificación y desarrollo de las mejoras recommendadas por fases (primero la Fase A).*

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
| **Sesiones 5–9** | Refinamientos UX, procesos/tiempo, WAL fuera, sync §4/§4.1 + conflictos, B6, CI | 🔄 Código hecho + suite verde; falta prueba manual |
| **Sesión 10 — 4 características** | Teclado completo, resumen/extensa, exportar tarjeta, limpieza/archivado | 🔄 Código hecho + suite verde; falta prueba manual |
| **Sesión 11 — seis correcciones** | S11-1…S11-5 **hechos**; **S11-6 abierto** (excepción al personalizar atajos) | 🔄 Ver `docs/plan-sesiones.md` |
| **Sesión 12** | Etiquetas globales, visor de conflictos, Ctrl+L, botón archivar, fix notas markdown | 🔄 Código hecho + suite verde; falta prueba manual |
| **Sesión 13 (2026-10-12)** | Tarjeta actual al clic, menú Ver, foco en etiquetas, selección visible, tooltips, tema obscuro | 🔄 Código hecho + **395/395**; falta prueba manual |
| **Sesión 14 (2026-10-12)** | Siete mejoras de producto B1–B5/B7/B8 (informe de tiempo, búsqueda global, recordatorios, captura rápida, plantillas+purga, color de etiquetas, subtareas) | 🔄 En curso; una por commit con push, ver la sección «Sesión 14» |
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
- **Higiene menor:** `deleteBoard` parametrizado (hoy sin
  `requireInCatalog`), aviso por límite de etiquetas (8, solo excepción de
  dominio).
- **Import con merge** en vez de duplicar el tablero: idea del usuario
  (2026-10-08) **pendiente de conversación**; propuesta completa, estado
  del código, precedentes de otros programas y preguntas abiertas en
  [`docs/import-merge-tableros.md`](docs/import-merge-tableros.md).

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

## Sesión 9 — conflictos de sync + documentación

- **Política de conflictos (§4) y UI de conflictos con copia (§4.1)** de
  `docs/sync-design.md`: implementados merge 3-vías (`SyncMerge`),
  orquestador `SyncService` (base guardada, fast-forward, merge, push y
  reintento sin `force`), puerto `ConflictCopyStore` + adaptador
  `JsonConflictCopyStore` (escribe `<bdDir>/conflicts/<boardId>-<epoch>.json`)
  y acción **Archivo → Sincronizar ahora** con aviso discreto en la UI.
  Conflictos resueltos por campo; **la edición gana al borrado**; se preserva
  la versión perdedora en la copia. Deudas documentadas: desempate sin
  `device_id` (comparación canónica), orden posicional (falta *fractional
  index*).
- **Tests:** 221/221 verdes (17 nuevos: `SyncMergeTest`, `SyncServiceTest`,
  `JsonConflictCopyStoreTest` + dobles `InMemorySyncRepository`,
  `RecordingConflictCopyStore`).
- **Documentación nueva:** [`docs/manual-usuario.md`](docs/manual-usuario.md)
  (manual de usuario completo) y
  [`docs/mejoras-propuestas.md`](docs/mejoras-propuestas.md) (mejoras
  priorizadas, **sin implementar**, con checkboxes para revisarlas al probar).
- **Blindaje del sync + B6 (misma sesión):**
  - `SyncMergePropertyTest` (9 tests, sin dependencias nuevas — el build es
    offline): sobre 150 tableros aleatorios sembrados verifica determinismo,
    idempotencia, invariancia ("un cambio de un solo lado nunca se pierde"),
    punto fijo, simetría del desempate y validez estructural (nunca campos
    null).
  - **Bug real encontrado y corregido** en `SyncMerge`: borrar la única
    columna en un lado y editar una tarjeta suya en el otro dejaba la tarjeta
    huérfana → `NullPointerException`. Ahora la columna que aloja a una
    tarjeta superviviente también se conserva ("la edición gana al borrado"
    se extiende al contenedor). Test de regresión incluido.
  - **B6:** clic en el chip de etiqueta filtra por ella (clic de nuevo lo
    quita); clave `card.label.chip.tip` en los 5 bundles.
  - **D3:** workflow `.github/workflows/ci.yml` (`mvn -B test`, JDK 21).
- **Pendiente:** prueba manual del usuario (sync con dos equipos, copias de
  conflicto, chip de etiqueta) y decidir qué mejoras de
  `docs/mejoras-propuestas.md` entran en la próxima sesión.

## Sesión 10 — las 4 características pedidas (2026-10-09)

> El usuario mandó de golpe cuatro peticiones. Se planificaron en
> [`docs/plan-sesiones.md`](docs/plan-sesiones.md) (sección «Sesión 10») y se
> desarrollaron **una por una, con commit + push por característica**.

1. **F1 — Navegación completa con teclado, sin botones** (`c0ed25f`).
   Fuera los tres botones de navegación de la toolbar. El mapa completo vive
   en un **filtro de eventos de la escena** (`onBoardKeyPressed`), no en
   aceleradores: un acelerador de JavaFX se evalúa *después* de que el
   control con foco trate la tecla, y esa es la razón por la que «con el
   teclado no se tenía esa funcionalidad» mientras los botones
   funcionaban. Teclas: `↓/↑` dentro de la columna (con vuelta),
   `Alt+←/→` entre columnas (ignora columnas vacías), `Ctrl+←/→` entre
   tableros, `1`–`9` = posición en la columna actual, `Enter` edita,
   `Alt+I`/`Alt+U` = flags ★/!, `Delete` = borrar con confirmación (el
   foco queda en la tarjeta que ocupa su lugar). La política está en
   **`ui/CardNavigator`** (Java puro, 13 tests) y el cableado en
   `KeyboardNavigationTest` (7). Al escribir en un campo o dentro de
   listas/combos las teclas no se secuestran; la vista de procesos
   conserva sus teclas propias.
2. **F2 — El resumen de 3 renglones y la extensa se veían iguales**
   (`5193d63`). Causa raíz: `MarkdownSummary` contaba **bloques** del AST,
   no líneas — un párrafo de 10 saltos era «1 bloque» y se pintaba entero.
   Ahora el presupuesto es de **líneas y caracteres** (`ui/markdown/
   LineBudget`, 8 tests) con corte y `…`, y los bloques/viñetas se
   separan con `\n` explícito: **`TextFlow` coloca a sus hijos en línea**,
   así que sin ese `\n` dos párrafos o dos viñetas salían pegados en UNA
   fila. Preview = 3 filas ≈ 120 caracteres; `FULL` sin límites.
3. **F3 — Exportar tarjeta txt/markdown/pdf** (`ac5c62a`). Diálogo con
   secciones (datos, lista de tareas, notas, registros de tiempo), notas
   seleccionables (**todas por defecto**) y formato. Contenido generado por
   **`ui/CardExporter`** (puro, 8 tests); el PDF es **texto seleccionable**
   paginado en A4 (`ui/pdf/CardPdfWriter`, PDFBox puro, 6 tests) — no una
   captura. Acceso: menú contextual → «Exportar tarjeta…».
4. **F4 — Limpieza de tableros** (`5dc2882`). **Archivar** = etiqueta
   reservada `Archivada` + casilla «Mostrar archivadas» en la barra de
   filtro (sin migración; el gate está en `matchesProcessFilter`, el único
   filtro por el que pasan render y teclado). **Borrar** = cada ruta de
   borrado (tarjeta, selección múltiple, vaciar/borrar columna, borrar
   tablero) mueve los adjuntos a `attachments-trash/<id>` ANTES de borrar
   la fila, y `refresh()` los **restaura si la tarjeta vuelve (Ctrl+Z)** —
   un borrado directo habría perdido archivos al deshacer. **Archivo →
   Limpiar archivos adjuntos huérfanos…** borra las carpetas cuya tarjeta
   no existe en ningún tablero (y no toca nada si un tablero no se lee).
   Notas y checklist van con la fila (ya estaban en la BD).

**Hallazgos de la sesión (todos corregidos salvo el último):**

- Los atajos guardados (`ui.shortcuts`) **se escribían pero nunca se
  leían**: `BoardService.globalShortcuts()` no tenía ningún llamante, así
  que reiniciar la app devolvía todo a los valores por defecto.
- El bucle de visibilidad del menú contextual de tarjeta ocultaba **todos**
  los ítems «plain» — incluido «Mover/copiar a otro tablero» y los
  separadores — hasta que la tarjeta tenía un override de vista.
- `TextFlow` fluye en línea: los bloques de la tarjeta sin `\n` explícito
  se dibujaban pegados (visible desde F2).
- Un `"\R"` escrito como `"\R"` en Java no compila (el separador de
  líneas del PDF tiene que ser `"\\R"`).
- **Conocido, sin tocar:** en la **vista de procesos** las archivadas siguen
  visibles (el grafo se calcula con todas las tarjetas); las teclas fijas
  nuevas no están listadas en la ayuda F1 (solo las configurables); el
  toggle «mostrar archivadas» es de sesión, no persiste.

**Estado:** suite **367/367 OK** (1 skip), ArchitectureTest e I18nCoverage
en verde, 22 claves i18n nuevas en los 5 bundles, todo commiteado y
push-eado (`a049298` plan, `c0ed25f`, `5193d63`, `ac5c62a`, `5dc2882`).
**Pendiente:** la prueba manual del usuario de las 4 características (la
suite no puede arrancar el toolkit JavaFX ni el diálogo real); las fases
B/C/D del plan de sesión siguen en espera.

---

## Sesión 12 (2026-10-11): etiquetas globales + cierre de pendientes

Pedidas por el usuario de una vez: gestor global de etiquetas, visor de copias
de conflicto (3.1 de `docs/adiciones-propuestas.md`), atajo de etiquetas,
botón de archivar y el fix del bug de notas con markdown. Todo hecho, una
funcionalidad por commit con push.

- [x] **Catálogo global de etiquetas** (`afb6163`). Las etiquetas son un único
  vocabulario por base de datos (`LabelCatalog` persistido en `app_setting`
  clave `labels.catalog`, sin migración). Menú **Etiquetas → Gestionar
  etiquetas…** para añadir, renombrar y quitar: renombrar/quitar barre las
  tarjetas de **todos** los tableros guardados y recarga el activo; las
  etiquetas ya existentes en tarjetas se adoptan al arrancar. Reservadas
  (Urgente/Importante/Archivada): se listan primeras y **no** se pueden
  renombrar ni quitar, ni renombrar otra hacia ellas; identidad sin
  distinguir mayúsculas («et1» = «ET1»). Los diálogos de tarjeta y masivo
  ofrecen el catálogo global.
  - **Recuperación incluida:** `BoardController.java` había quedado con 2.6k
    líneas perdidas de una sesión anterior (restaurado desde git),
    `GlobalLabelsService` estaba corrupto (reescrito), `BoardService` tenía un
    par de métodos duplicados y `Card.setLabels` había perdido el tope de 8
    etiquetas y rechazaba las etiquetas del sistema que usan ★/! (ambos
    restaurados).
- [x] **Visor de copias de conflicto** (`9606a64`), cierra **3.1**: menú
  **Tableros → Ver copias de conflicto…** con lista + manifiesto campo a
  campo (local → remoto) para el tablero activo. `ConflictCopy` movido a
  `application.sync` (regla ArchUnit) y NPE del panel de detalle corregido.
  Ver `docs/progress-conflict-viewer-sesion.md`.
- [x] **Atajo Ctrl+L** (`982819b`), acción configurable `LABELS` como las
  demás: abre el diálogo añadir/quitar etiquetas de la selección, o de la
  tarjeta enfocada con las flechas.
- [x] **Botón 📦 archivar** (`7328bb4`) en la barra de herramientas: alterna
  la etiqueta «Archivada» en la selección (o en la tarjeta enfocada); si
  todas están archivadas la quita. Un paso deshacerable.
- [x] **Fix notas markdown** (commit `fix(notes): …`). El separador de notas
  dejó de ser «## » (también era markdown) y pasó a «%% »: pegar texto con
  encabezados `#`/`##` ya **no** fragmenta la lista de notas. Los textos
  guardados con el formato antiguo (que empiezan por «## ») siguen
  parseando con el separador legado para no perder su estructura.

**Hallazgos/sorpresas:** `LabelCatalog` (nuevo en una sesión previa) tenía
errores que impedían compilar (import `Optional`, `kindFor` no estática en un
record, `Card.MAX_LABEL_LENGTH` privado); `I18nCoverageTest` fallaba en HEAD
por la clave dinámica `i18n.text("prefs.scheme." + slot)` (el scan ahora
ignora claves terminadas en «.»); `CurrentConflicts` dependía de
infrastructure (ArchUnit) y su test no limpiaba el tmpdir.

**Estado:** suite **385/385 OK** (1 skip: `PhpSyncApiEndpointTest`), jar
empaquetado, 5 commits con push. **Pendiente manual:** probar la app
(`kanban.bat`): gestor de etiquetas, Ctrl+L, 📦 y las notas pegadas con
markdown. **Sin tocar (grandes, para sesiones dedicadas):** sync automático
(3.4), device_id (3.2), fractional index (3.3), jpackage (4.3).

---

## Sesión 13 (2026-10-12): cierre de la Sesión 11 + las seis mejoras pedidas

> **Relación con el plan:** cierra el punto **S11-5** de
> [`docs/plan-sesiones.md`](docs/plan-sesiones.md) (clic = tarjeta «actual») y
> añade peticiones nuevas del usuario, todas entregadas en la misma sesión.

### Lo pedido y su estado

- [x] **Tarjeta «actual» con el ratón.** Un clic simple (fuera del modo
  selección múltiple) fija la tarjeta como actual — el mismo estado que mueven
  las flechas — así que resalta y los atajos (Ctrl+L, archivar, ★/!) actúan
  sobre lo que se acaba de clicar. Solo una tarjeta puede ser la actual: al
  cambiar de tablero se pierde y `Esc` la suelta. La clase CSS es propia
  (`.card-current`), no depende del foco transitorio de JavaFX.
  `BoardController.onCardSelected/isCardCurrent`, `CardViewBuilder` y el clic
  de la vista de procesos.
- [x] **Foco en el campo de la ventana de etiquetas.** El diálogo «Etiquetas en
  lote» enfoca el campo al mostrarse (antes lo tenía «Aceptar»); el prompt de
  una línea (añadir/renombrar etiqueta, tablero o proceso) también enfoca su
  campo. `Dialogs.bulkLabelsDialog` y `Dialogs.promptText`.
- [x] **Menú «Ver» + atajos.** Menú nuevo con «Tablero de tarjetas»
  (`Ctrl+Shift+1`) y «Diagrama de procesos» (`Ctrl+Shift+2`), con el punto de
  radio marcando el modo activo; los tres accesos (menú, atajos y botón ⇄)
  pasan por `onSelectView(...)`. Claves nuevas en los 5 bundles y filas nuevas
  en la ayuda F1.
- [x] **Tarjetas seleccionadas distinguibles.** Doble causa: la regla
  `.card-selected` (una clase) perdía frente a `.card.pk-color-*` (dos) y el
  color se aplicaba *inline* (que gana al CSS). Ahora la regla es
  `.card.card-selected` (fondo azul + borde sólido + halo) y una tarjeta
  seleccionada no recibe estilos inline. En los dos temas.
- [x] **Tooltips.** El texto crudo era la clave `bulk.process`, que no existía
  en ningún bundle: añadida a los 5 idiomas. Además las banderas ★/! mostraban
  las etiquetas del dominio («Importante»/«Urgente») en *cualquier* idioma
  porque el argumento `tipKey` nunca se leía: ahora usan
  `card.flag.important/urgent`. `I18nCoverageTest` gana un segundo test que
  revisa también las claves pasadas como argumento a helpers
  (`toolButton("⛭", "bulk.process")`), que el escaneo anterior no veía.
- [x] **Descripción legible en tema obscuro.** Los `Text` que construye
  `MarkdownSummary` no llevaban la clase de estilo `text`, así que la regla
  `.card-description .text` de `dark.css` **nunca aplicaba** (el fix de la
  sesión 5 era inerte): el relleno quedaba negro. Ahora llevan la clase;
  verificado con test que mide el color real.

### Hallazgos técnicos de la sesión (verificados)

1. **Un `Text` creado en código no tiene clase de estilo propia** → los
   selectores `.card-description .text` no casan. Medido con un test que aplica
   `dark.css` real: `fill = 0x000000ff` (negro) antes, `#c3cbd6` después.
2. **Un `ScrollPane` recién construido no tiene skin**, así que durante un
   `refresh()` su contenido *no* está entre sus hijos: cualquier recorrido del
   grafo (como el que pinta la tarjeta actual) debe pasar por
   `ScrollPane.getContent()`. Pillado por la prueba de comportamiento, no
   leyendo código.
3. **Especificidad CSS de JavaFX**: `.card.pk-color-*` (dos clases) gana a
   `.card-selected` (una) sin importar el orden; de ahí que la selección fuera
   invisible en tarjetas con color.
4. **`I18n.text` devuelve la clave** cuando falta en el bundle (comportamiento
   conocido): lo nuevo es que las claves pasadas como argumento eran
   invisibles para `I18nCoverageTest`; ahora hay una prueba que las caza — y
   detectó una regresión propia en el acto (`menu.processes` y `help.cardview`
   borradas al insertar las claves nuevas, restauradas).

### Tests

- Suite completa **395/395 OK** (1 skip: `PhpSyncApiEndpointTest` sin servidor
  PHP).
- Nuevos: `CardCurrentSelectionTest` (5) levanta un `BoardController` real
  sobre una BD temporal en un `Scene` y **lanza clics de ratón y acciones de
  menú reales**: clic → tarjeta actual, unicidad, sobrevive a un rebuild, en
  modo selección marca seleccionada (no actual) y el menú «Ver» cambia de modo
  con sus atajos. `CardThemeCssTest` (4) aplica los CSS reales y comprueba
  descripción legible (claro y obscuro), selección y tarjeta actual frente al
  acento de color. `I18nCoverageTest` suma el guardián de claves indirectas.
- Jar portable regenerado: `mvn -o -Pportable -DskipTests package`.

### Checklist de prueba manual (usuario)

1. **Tarjeta actual:** clic en una tarjeta → se resalta (borde + halo) y `Ctrl+L`
   abre las etiquetas de *esa* tarjeta; clic en otra → el resalte se mueve y
   solo queda una; `Esc` lo suelta; cambiar de tablero lo limpia.
2. **Foco en etiquetas:** con la selección activa, `Ctrl+L` → el cursor está en
   el campo de texto (puedes escribir sin tocar el ratón).
3. **Menú Ver:** Alt → «Ver» → «Diagrama de procesos» (o `Ctrl+Shift+1/2`) →
   cambia de vista y el modo activo aparece marcado; F1 lista los dos atajos.
4. **Selección:** botón ☐ de una columna → clic en varias tarjetas → todas se
   ven azules con borde sólido, inconfundibles frente a las no seleccionadas
   (probarlo también con una tarjeta de color).
5. **Tooltip del botón de proceso:** en modo selección, pasa el ratón por el
   botón «⛭ Proceso» de la barra → texto legible, no una clave con puntos;
   igual con ★/! en cualquier idioma distinto del español.
6. **Tema obscuro:** `Ctrl+D` → la descripción de las tarjetas se lee (gris
   claro), no negra.
7. **(Regresión) Arranque y datos:** abrir con tu BD de siempre → todo sigue
   igual; los cambios de vista, selección y filtros no tocan los datos.

### Pendiente y conocido (sin tocar en esta sesión)

- **S11-6 abierto (bug real):** «Ayuda → Personalizar atajos de teclado»
  sigue llamando a `GlobalShortcuts.Action.fromStorageNameOrThrow(...)` con
  nombres de enum (`NEXT_CARD`), que solo acepta nombres de almacenamiento
  (`next`): el diálogo y su botón «Restaurar» pueden lanzar
  `IllegalArgumentException`. Fix planificado (tolerar ambos nombres + llevar
  el enum en la fila) sin hacer.
- El tooltip del selector de color muestra el nombre interno del color
  (`blue`): no existen claves i18n de colores (propuesta B9).
- El foco del diálogo de etiquetas no tiene prueba automática (es modal);
  verificado por revisión (propuesta D5).
- Sin commit: el repo solo se commitea cuando el usuario lo pide.

---

## Sesión 14 (2026-10-12): las siete mejoras de producto B1–B5/B7/B8

Pedido del usuario, en bloque y con la instrucción de **documentar y
commitear+pushear cada mejora al terminarla**. El plan detallado vive en
[`docs/plan-sesion-14.md`](docs/plan-sesion-14.md); aquí queda el estado.

| # | Mejora | Estado | Commit |
|---|---|---|---|
| B1 | Informe de tiempo (horas por proceso y por semana, export CSV/PDF) | ⬜ planificada | — |
| B2 | Búsqueda global (Ctrl+Shift+F, cruza tableros; backend `findCards` ya existía) | ⬜ planificada | — |
| B3 | Recordatorios de vencimiento + vistas «vence hoy / esta semana» + reagendado rápido | ⬜ planificada | — |
| B4 | Captura rápida con sintaxis en el alta de tarjeta | ⬜ planificada | — |
| B5 | Plantillas propias guardables + purga de tarjetas archivadas | ⬜ planificada | — |
| B7 | Registro de etiquetas con color elegido (aprovecha para cerrar B9) | ⬜ planificada | — |
| B8 | Subtareas jerárquicas (un nivel, migración **V10**) | ⬜ planificada | — |

Decisiones de diseño tomadas en el plan (y su porqué): `#` descartado en la
captura rápida a favor de `+etiqueta` porque `#` ya identifica etiquetas de
**proceso**; los recordatorios de vencimiento son **dentro de la app** (banda
no modal + filtros), no del sistema operativo, porque JavaFX no trae
notificaciones y `SystemTray` no es verificable en la suite; las subtareas son
de **un solo nivel**; el informe de tiempo cubre el **tablero activo** y 4
semanas por defecto.
