# PLAN — Plan de trabajo, checklist y estado

> **Documento único de trabajo.** Reúne el **plan**, el **checklist** de todo lo
> que falta, el **estado** (hecho / en curso / pendiente) y una **bitácora** de
> avance con el commit de cada tarea, para que se pueda retomar el trabajo
> aunque la sesión se corte. Consolida lo que antes estaba repartido entre
> `docs/plan-sesiones.md`, `docs/plan-sesion-14.md`,
> `docs/pending-mejoras-propuestas.md`, `docs/mejoras-propuestas.md`,
> `docs/adiciones-propuestas.md` y `docs/pendientes.md` (todos retirados el
> 2026-10-13).
>
> **Regla de mantenimiento:** al terminar cada tarea → marcar su casilla, anotar
> la fila en la **bitácora** (§3) y **commit + push**. Se documenta **antes** de
> cerrar la sesión.
>
> **Estado (2026-10-14):** rama `ImprovementsV1`; suite **589/589 OK** (1 skip:
> endpoint PHP sin servidor); migraciones hasta **V11**. Documentación
> consolidada; hechos **T1–T9, T12–T16, T23** y **T21a** (instalador
> `jpackage`); reordenado el **menú principal** (N-15). Siguiente por prioridad:
> **T17** (backup/integridad, §8.1), luego **T10/T11**, **T18/T19**, **T22**,
> **T20** y **T21b**.

---

## 🗂️ Leyenda

| Marca | Significado | | Marca | Significado |
|---|---|---|---|---|
| ✅ | Hecho y verificado | | 💬 | Pendiente de conversación |
| 🔄 | Parcial (falta completar) | | 🧪 | Falta solo la prueba manual |
| ⬜ | No empezado | | 🐞 | Bug: comportamiento incorrecto hoy |

Identificadores: **T-n** = tarea del plan · **N-n** = requisito nuevo del
2026-10-13 · **B-n** = mejora de producto · **H-n** = pendiente heredado ·
**C-n** = conversación pendiente · **PR-n** = prueba manual.

---

## 1. ✅ Checklist maestro

Todo lo pendiente, en el orden en que se va a desarrollar (§2).

### Fase 1 — Bugs y correcciones rápidas

- [x] **T1 · BUG-1** ✅ «Ayuda → Personalizar atajos de teclado» lanza
  excepción (`fromStorageNameOrThrow` recibe nombres de enum). §5.1.
- [x] **T2 · N-1 + N-2** ✅ En modo selección: **ocultar la barra de filtros**
  y alternarla con la de selección; **Esc borra la selección** actual. §6.1.
- [x] **T3 · N-10 + N-11** ✅ Detalle de tarjeta: **Esc = Cancelar**; **mover
  «Añadir nota» y «Guardar nota»** junto al botón editor/render («Eliminar
  nota» se queda abajo). §6.5.
- [x] **T4 · N-12 + BUG-2** ✅ Filtro **«NO tiene la etiqueta X»** y **«no
  tiene ninguna etiqueta»**. §6.6.

### Fase 2 — Filtros y vencimientos

- [x] **T5 · N-6 + B3** ✅ **Vencimientos**: filtros «vencidas / vence hoy /
  esta semana», aviso discreto al arrancar y **reagendado rápido** en el menú de
  la tarjeta. §6.3 y §7.1.

### Fase 3 — Columnas y navegación

- [x] **T6 · N-13** ✅ **Conservar la posición de scroll** de la columna al
  seleccionar una tarjeta. §6.7.
- [x] **T7 · N-14** ✅ **Botón por tarjeta** para elegir qué ver en la columna:
  descripción / tareas / comentarios. §6.7.
- [x] **T8 · N-3** ✅ **Quitar el botón «agregar columna»** de la toolbar; la
  función vive en el menú **Tableros** y en cada columna (**añadir a la
  izquierda / a la derecha**). §6.2.

### Fase 4 — Tarjetas (barra principal y lote)

- [x] **T9 · N-5** ✅ **Asignar una tarjeta a un proceso sin modo selección**
  (menú contextual de la tarjeta → **Proceso ▸**; «Sin proceso» la saca). §6.2.
- [x] **T10 · N-4** ✅ Botón **copiar / pegar tarjetas** (pega como tarjetas
  nuevas en la columna actual). §6.2.

### Fase 5 — Vista de proceso

- [x] **T11 · N-7 + N-9** ✅ Vista de proceso: **nombre (y proceso) al crear
  tarjeta**; **limitar la selección** a las tarjetas del proceso. §6.4.
- [x] **T12 · N-8** ✅ 🐞 **Se borraban las relaciones** al hacer clic en una
  flecha de la vista de procesos: ahora **pide confirmación** antes de quitarla.
  §6.4.

### Fase 6 — Mejoras de producto (sesión 14 en curso)

- [x] **T13 · B4** ✅ **Captura rápida con sintaxis** al dar de alta. §7.2.
- [x] **T14 · B5** ✅ **Plantillas propias** + **purga de archivadas**. §7.3.
- [x] **T15 · B7** ✅ **Color por etiqueta** elegido en el gestor (cierra **B9**,
  nombres de color traducidos). §7.4.
- [x] **T16 · B8** ✅ **Subtareas jerárquicas** de un nivel (migración **V10**).
  §7.5.
- [x] **T23 · B10** ✅ **Recordatorios programables por tarjeta**: varias fechas
  (con hora opcional) elegidas en un **calendario** de hoy a la fecha límite, o
  rellenadas por **diario / cada semana** (migración **V11**). §7.6.
- [x] **T24 · B11** ✅ **Estado «terminada»** de la tarjeta (`completedAt`,
  migración **V12**, auto al soltar en la columna finalizada) + **seis
  correcciones** de vencimientos (CSS del badge, filtros en la vista de
  procesos, recuento del aviso, fecha en el detalle, aviso de fecha pasada,
  sync de recordatorios). §7.7.

### Fase 7 — Robustez y distribución

- [ ] **T17 · H-2 + H-3** ⬜ **Backup automático rotativo** de la BD +
  **verificación de integridad** (`PRAGMA integrity_check`) con reparación. §8.1.
- [ ] **T18 · H-7 + H-8** ⬜ **Logging persistente rotativo** y arreglo de
  **JaCoCo** (no instrumenta con JDK 25). §8.2.
- [ ] **T19 · H-11 + H-10** ⬜ **Ayuda F1 completa** (teclas fijas de tarjeta) y
  prueba automática del **foco inicial de los diálogos**. §8.2.
- [ ] **T20 · H-4 + H-5** ⬜ Sync: `device_id` (3.2), *fractional index* (3.3),
  sync automático (3.4), outbox/deltas, lápidas, cifrado. §8.3.
- [x] **T21a · H-6** ✅ **Empaquetado nativo** (`jpackage`): `instalador.bat`
  reproducible (app-image si no hay WiX; `.exe` con WiX). §8.3.
- [ ] **T21b · H-6** ⬜ **Auto-actualización** (solo si el usuario la quiere).
  §8.3.
- [ ] **T22 · H-1** ⬜ Confirmar si la **búsqueda en el tablero activo** (fase B
  histórica) se da por cerrada con **B2** (búsqueda global). §8.4.

### Pendientes de conversación (no implementar sin decidir)

- [ ] 💬 **C-1** Import con *merge* en vez de duplicar tablero.
  [`docs/import-merge-tableros.md`](docs/import-merge-tableros.md).
- [ ] 💬 **C-2** Impresión de tableros (export HTML vs `PrinterJob`).
- [ ] 💬 **C-3** Avisos de vencimiento **y recordatorios** (T23 ✅ hecho con
  banda + ventana internas): ¿añadir **bandeja del sistema** (`SystemTray`)
  además, para que el aviso se vea con la ventana minimizada?
- [ ] 💬 **C-4** Informe de tiempo: ¿solo tablero activo o todos?
- [ ] 💬 **C-5** H-12 (menor): archivadas visibles en la vista de procesos; el
  toggle «mostrar archivadas» no persiste; resumen del frente configurable;
  filtro guardado por tablero; nombres de color en el tooltip de color.
- [ ] 💬 **C-6** Vista de procesos: clic que abre el detalle de golpe + panel
  inferior confuso. **Hecho: A1 + B1 + C1** (§7.8); queda **C2** (menú
  contextual): lluvia de ideas en
  [`docs/process-view-context-menu.md`](docs/process-view-context-menu.md).

### Prueba manual del usuario (el código ya está)

- [ ] 🧪 **PR-1** Sesiones 4–13: modos de vista, detalle/notas/checklist,
  exportar tarjeta, archivar/borrar, etiquetas, atajos, tema obscuro, sync con
  dos equipos. §9.
- [ ] 🧪 **PR-2** Sesiones 11–13: esquema de colores + vista previa, notas con
  markdown, clic = tarjeta «actual», menú Ver, selección visible.
- [ ] 🧪 **PR-3** Sesión 14: informe de tiempo y búsqueda global.
- [ ] 🧪 **PR-4** Sesión 15 (T23 · B10): poner recordatorios en una tarjeta con
  vencimiento, ver la ventana de pendientes al arrancar y **que el aviso salte
  a la hora exacta con la app abierta** (lo único que los tests no pueden
  comprobar: el disparo con el reloj real).

---

## 2. 🚀 Plan de desarrollo (orden, alcance y criterio de hecho)

**Criterio de orden:** primero los **bugs** (bloquean funciones que ya existen),
luego las **correcciones pequeñas y visibles** (barato, se nota), después los
**filtros y la vista de columnas** (uso diario), luego las **mejoras de producto
B4–B8** (que ya tienen diseño) y al final la **robustez** (backup, sync,
empaquetado).

**Reglas comunes a toda tarea** (no se repiten en cada una):
1. Arquitectura hexagonal (`ArchitectureTest`): dominio puro; `application` sin
   JavaFX/JDBC/UI; `ui` sin JDBC/infraestructura.
2. i18n en los **5** bundles (base + EN/ES/DE/FR): lo exige `I18nCoverageTest`.
3. **Sin dependencias nuevas** (el build es offline) ni cambios de build salvo
   que la tarea lo exija.
4. **Test nuevo** que falle sin el cambio (mínimo: lógica pura + una prueba de
   que la UI está cableada); **suite completa verde** antes del commit.
5. **Cierre obligatorio de cada tarea (regla permanente del usuario).** Antes de
   dar por terminado algo — una **característica nueva**, el **fix de un
   problema** o un **avance importante** — hay que: ① **documentar el avance y
   los hallazgos** (bitácora §3, checklist §1, el detalle que cambie y
   `docs/manual-usuario.md` si se ve en la UI); ② hacer **el commit** (en
   inglés, con un mensaje que explique *el porqué*); y ③ **hacer push**. En
   resumen: **nunca queda trabajo terminado sin documentar ni sin subir**.
   Los hallazgos nuevos se anotan con su *porqué* aquí o en
   `docs/roadmap-and-decisions.md`.
6. No romper BDs existentes del usuario (datos reales).

| # | Tarea | Prioridad | Migración | Riesgo |
|---|---|---|---|---|
| T1 | BUG-1 atajos | 🔴 Alta | no | bajo |
| T2 | N-1/N-2 selección + Esc | 🔴 Alta | no | bajo |
| T3 | N-10/N-11 detalle | 🟠 Media | no | bajo |
| T4 | N-12 filtro «sin etiqueta» | 🟠 Media | no | bajo |
| T5 | N-6/B3 vencimientos | 🟠 Media | no | medio |
| T6 | N-13 scroll de columna | 🟠 Media | no | bajo |
| T7 | N-14 contenido por tarjeta | 🟡 Media | no | medio |
| T8 | N-3 botón de columna | 🟡 Media | no | bajo |
| T9 | N-5 proceso sin selección | 🟡 Media | no | bajo |
| T10 | N-4 copiar/pegar | 🟡 Media | no | medio |
| T11 | N-7/N-9 vista de proceso | 🟡 Media | no | medio |
| T12 | N-8 relaciones | 🟠 Media | no | medio |
| T13 | B4 captura rápida | 🟡 Media | no | bajo |
| T14 | B5 plantillas + purga | 🟡 Media | no | medio |
| T15 | B7 color por etiqueta | 🔵 Baja | no | medio |
| T16 | B8 subtareas | 🔵 Baja | **sí (V10)** | alto |
| T23 | B10 recordatorios por tarjeta | 🟡 Media | **sí (V11)** | medio |
| T24 | B11 tarjeta «terminada» + arreglos de vencimientos | 🟠 Media | **sí (V12)** | medio |
| T17 | H-2/H-3 backup + integridad | 🟠 Media | no | medio |
| T18 | H-7/H-8 logging + JaCoCo | 🟡 Media | no | bajo |
| T19 | H-11/H-10 ayuda + foco | 🔵 Baja | no | bajo |
| T20 | H-4/H-5 sync avanzado | 🔵 Baja | **sí** | alto |
| T21a | H-6 jpackage (instalador) | 🔵 Baja | no | bajo |
| T21b | H-6 auto-actualización | 🔵 Baja | no | medio |
| T22 | H-1 confirmar fase B | — | no | — |

El detalle de alcance/archivos/tests de **T1–T12** está en §6 y en las entradas
por tarea; el de **T13–T16** (B4–B8) y **T23** (B10) en §7; el resto en §8.

---

## 3. 📓 Bitácora de avance

> Cada fila = una tarea cerrada, con su commit. Aquí se ve **dónde se quedó**.
> La tarea en curso se marca como «🔄 en curso» al empezarla.

| Fecha | Tarea | Estado | Commit / notas |
|---|---|---|---|
| 2026-10-20 | **C-6** vista de procesos: A1 + B1 + C1 (§7.8) | ✅ | `fix(process-view): one click selects, double click opens, single bottom inspector (A1+B1+C1)` — **A1:** el clic en la tarjeta del diagrama ya no abre el detalle (era el `setOnMouseClicked` del título): un clic la selecciona y enfoca (y la hace «actual»), solo el **doble clic** abre, igual que el tablero; `Enter` sigue abriendo el editor. **B1:** el inspector de relaciones pasa de **uno por fila** a **una sola banda al pie de la vista**, oculta y fuera del layout hasta que hay tarjeta enfocada, con solo la cabecera (título: clic = enfocar, doble clic = abrir) y las dos listas de enlaces; fuera los 4 botones duplicados y la ayuda de teclado permanente (`process.inspector.hint` → `process.inspector.caption` ×5 idiomas). **C1:** `.process-card.card-current` marca la tarjeta actual en ambos temas. `ProcessViewInteractionTest` (3) → suite **622/622** (1 skip) |
| 2026-10-19 | **T24 · B11** tarjeta «terminada» (V12) + seis correcciones de vencimientos | ✅ | `feat(cards): finished-card state with auto-complete in the done column (V12) + six due-date fixes` — **A:** `Card.completedAt` (`LocalDate`, null = pendiente) con `isDone()/complete/reopen`; `Board.moveCard/moveCardsToColumn` **completan al entrar** en la columna finalizada y **reabren al salir** («hoy» como **parámetro**, dominio sin reloj); `CompleteCardCommand` (1 undo); migración **V12** (`card.completed_at`) + binding/lectura + componente en `CardSnapshot` con ctor retrocompatible; LWW en `SyncMerge`; **✓** en el frente (patrón ★/!), menús de tarjeta y «Tarjetas», clase `card-done` (apagada + tachada) y toggle **«Mostrar terminadas»** (barra + menú Ver) enganchado en `matchesProcessFilter` (un solo gate: columnas, navegador, aviso y búsqueda); `ReminderPlan.dueNow` ignora hechas. **Correcciones:** **B1** `.card-due`/`.card-due-overdue` y las bandas `sync/due/reminder-notice` **no existían en ningún CSS** → añadidas a ambos temas; **B2** la vista de procesos **no aplicaba ningún filtro** → `BoardController.matchesVisibleFilters` (un solo gate para ambas vistas); **B3** el aviso de vencimientos contaba solo con filtro de proceso → ahora corre la misma cadena que las columnas; **B4** la fecha (y «terminada el») se ve en la cabecera del detalle; **B5** el DatePicker del formulario **avisa en rojo** con fecha pasada (sin bloquear); **B6** `SyncMerge.cardKey` incluye recordatorios + `completedAt` (una edición solo-recordatorio ya no pierde contra un borrado) y **poda** los recordatorios posteriores a la fecha ganadora tras el merge. **Hallazgos:** ① la prueba de UI más útil fue la de filtros: el toggle «mostrar» debe **recuperar** las hechas de los filtros de vencimiento también (decisión ②); ② PowerShell `Add-Content` escribe ANSI → el CSS añadido corrompió UTF-8 (`CardKeyboardFocusTest` lo pilló); ③ `refresh()` era `private` → package-private para tests. `CompleteCardTest` (13), `ReminderPlanTest` (+1), `SyncMergeTest` (+4), `SqliteBoardRepositoryTest` (+1), `CompletedCardUiTest` (4), `CardThemeCssTest` (+4) → suite **619/619** (1 skip) |
| 2026-10-19 | **T10 · N-4** copiar/pegar tarjetas + **T11 · N-7/N-9** vista de proceso | ✅ | `feat(cards)+feat(process-view)+fix(ui): copy/paste cards, richer process creation, markdown format toolbar and clearable column background` — **T10:** portapapeles interno (`cardClipboard`) + **Ctrl+C/Ctrl+V**, entrada **Copiar tarjeta** en el menú de la tarjeta y botón **Pegar** en la cabecera de cada columna; pega **una tarjeta nueva** en la columna actual en **1 undo** (`DuplicateCardCommand` + `BoardService.duplicateCard`), copiando título/descripción/color/vencimiento/etiquetas/notas/checklist/proceso y **sin** enlaces. **T11:** la flecha verde abre el diálogo de tarjeta con el **proceso del ancla preseleccionado** (ya no crea «Nueva tarjeta» vacía) y «elegir existente» filtra por proceso (`linkCandidatesFor`). **UI:** `MarkdownEditor` gana botones **B/I/H/lista/enlace/código/limpiar** y pierde el tooltip invasivo; el «ninguno» del color de fondo de columna ahora **limpia** el fondo. `ProcessRelationDialogTest` (3) → suite **592/592** (1 skip) |

| 2026-10-13 | Documentación consolidada en un solo documento + borrado de docs redundantes | ✅ | `docs: consolidate all planning into one document (PLAN.md)` |
| 2026-10-13 | **T1 · BUG-1** atajos de teclado | ✅ | `fix(shortcuts): resolve actions by enum or storage name` — `fromNameOrThrow` acepta ambos nombres, la fila del diálogo lleva el enum (ya no re-parsea una clave i18n); +6 tests → suite **429/429** |
| 2026-10-13 | **T2 · N-1/N-2** barras alternas + Esc | ✅ | `feat(selection): alternate filter and selection bars, Esc drops the selection` — la barra de filtros se oculta en modo selección y vuelve al salir (también en `refresh()`); la tecla configurada de salida actúa en etapas (desmarca → sale del modo → suelta el foco). `SelectionModeBarsTest` (3) → suite **432/432** |
| 2026-10-13 | **T3 · N-10/N-11** detalle de tarjeta | ✅ | `feat(card-detail): Esc cancels, note buttons move to the editor header` — manejador de Esc en burbuja (un campo o autocompletado que consuma la tecla la conserva); `MarkdownEditor.addHeaderButton` coloca Añadir/Guardar junto al toggle y Eliminar se queda abajo. `CardDetailWindowTest` (3) → suite **435/435** |
| 2026-10-13 | **T4 · N-12/BUG-2** filtro «sin etiqueta» | ✅ | `feat(filter): «none of these» and «without labels» label modes` — `LabelFilter.Mode.EXCLUDE` + `withoutLabels()`; el modo del filtro pasa a ser un enum (valor estable, texto localizado) y el campo de texto se desactiva en «Sin etiquetas». `LabelFilterTest` (+2), `LabelFilterModeUiTest` (4, con tarjetas reales filtradas) → suite **441/441** |
| 2026-10-13 | **T6 · N-13** scroll de columna | ✅ | `fix(columns): keep each column's scroll position across a refresh` — el controlador recuerda el `vvalue` de cada columna antes de reconstruirla y el `ScrollPane` nuevo se crea con esa posición. `ColumnScrollTest` (2, con 40 tarjetas y clic real) → suite **455/455** |
| 2026-10-13 | **T5 · N-6/B3** vencimientos | ✅ | `feat(due): overdue/today/week filters, startup reminder and quick reschedule` — `DueDigest` (puro: rangos y conteos), `Board.rescheduleCard` + `RescheduleCardCommand` (1 undo), tres toggles mutuamente excluyentes en la barra, aviso no modal clicable y submenú «Reagendar» en la tarjeta. **Hallazgo:** el filtro de la barra solo existía en `visibleColumnsCards()` (navegador); el render de la columna aplica sus filtros en `ColumnViewBuilder`, así que el de vencimientos se añadió también ahí (test de UI lo pilló). `DueDigestTest` (5), `RescheduleCardTest` (4), `DueFilterUiTest` (3) → suite **453/453** |
| 2026-10-13 | **T7 · N-14** contenido por tarjeta | ✅ | `feat(card-front): pick the content of each card (description, tasks or notes)` — `CardViewSettings.Content` (DESCRIPTION / CHECKLIST / NOTES) guardado como override por tarjeta en el mismo JSON (sin migración: un archivo sin la sección sigue mostrando la descripción); botón en la tarjeta con menú de radio («comentarios» = las **notas**, confirmado por el usuario) y las tareas marcables desde el frente en 1 undo. `CardViewSettingsTest` (12) + `CardContentUiTest` (3) |
| 2026-10-13 | **T8 · N-3** botón de columna | ✅ | `1c0e5cb` («Realizado T7 y T8…»; el mensaje se quedó corto: la tarea **sí** quedó entera) — el botón ➕ de la toolbar desaparece; la función queda en **Tableros → Agregar columna** y en el menú ⋯ de cada columna, con **añadir a la izquierda / a la derecha** (también en columnas colapsadas). `Board.addColumn(..., insertIndex)` + `AddColumnCommand`/`BoardService.addColumnAt` insertan en el índice pedido en **un** paso de undo. `ColumnInsertAtTest` (4) + `ColumnAddMenuTest` (3) → suite **471/471** |
| 2026-10-13 | **T13 · B4** captura rápida con sintaxis | ✅ | `feat(capture): quick card capture with inline syntax` — `application/QuickCapture` (puro: `+etiqueta`, `!`/`*`, `@hoy/@mañana/@+3d/@ISO`, fecha inválida se queda en el título, signos dentro de palabras intactos) + `QuickCaptureDialog` (foco en el campo, **vista previa en vivo**, Enter crea y **Más opciones…** precarga el `CardForm` completo); el ➕ de la columna abre la captura rápida y **Ctrl+➕** / la entrada del menú de columna abren el diálogo completo. **Hallazgo:** la vista previa NO puede vivir en `QuickCapture` (necesita i18n y la capa `application` no puede depender de `ui`), así que el formateo está en el diálogo; el parseo sí es puro. `QuickCaptureTest` (13) + `QuickCaptureDialogTest` (4) → suite **488/488** |
| 2026-10-13 | **T14 · B5** plantillas propias + purga de archivadas | ✅ | `feat(board): user templates and archived-card purge` — `application/BoardTemplate` (puro, JSON propio: columnas con color/WIP/hecho, procesos y modo de vista; **sin tarjetas**), persistido en `board.template.<nombre>`; `BoardService.templates/hasTemplate/saveAsTemplate/removeTemplate/createBoardFromTemplate`; menú **Tableros → Guardar como plantilla… / Plantillas → (aplicar · eliminar)** y las propias dentro del selector de «nuevo tablero». Purga **Tableros → Borrar tarjetas archivadas…** (`archivedCardCount`, `purgeArchivedCards`, adjuntos a la papelera). **Hallazgos:** ① el puerto `SettingsStore` no tenía `remove`, se añadió (SQLite + doble en memoria); ② `BoardColor` **no** es un enum sino un record con hex, así que la plantilla guarda el **hex** (soporta colores propios) y se relee con `fromStored`; ③ el undo es **por tablero**, así que la purga borra tablero a tablero (un paso de undo en cada uno) y restaura el tablero activo al terminar; ④ la vista previa de color de `WipLimit` va como entero (0 = ilimitado). `BoardTemplateTest` (4) + `ArchivedPurgeTest` (5) → suite **497/497** |
| 2026-10-13 | **T15 · B7** color por etiqueta (cierra B9) | ✅ | `feat(labels): pick a color per label (closes B9)` — el gestor de etiquetas gana un selector de **color** (8 colores de la paleta + **Automático** = borra la regla) que escribe la regla de estilo para **claro y obscuro** (`setLabelStyle`/`clearLabelStyle`) con el color de texto calculado por contraste; `ui/LabelChipStyle` (puro) decide el chip: regla del usuario si existe, si no la clase por hash de siempre. **B9:** los nombres de color ya se traducen (`color.blue`…`color.gray` + `labels.color`/`labels.color.auto`) en el selector de color y en el gestor. **Hallazgo:** el catálogo y las reglas `ui.label.` ya existían (y se editan en Preferencias), así que el hueco era solo el chip + el gestor, no el almacenamiento. `LabelChipStyleTest` (5) → suite **502/502** |
| 2026-10-14 | **T12 · N-8** bug de relaciones en la vista de procesos | ✅ | `fix(process-view)+feat(cards): ask before unlinking and assign a process from the card menu (T12 · N-8, T9 · N-5)` — el clic en un conector **enlazado** ya no borra la relación en silencio: pide confirmación (`process.unlink.confirm` con los dos títulos), mientras que el conector **sugerido** (punteado, no destructivo) sigue enlazando con un clic; el área de clic invisible de 16 px gana la clase `process-edge-hit` y su tooltip anuncia la confirmación. **Hipótesis descartada:** no había borrado múltiple (verificado en `Board.unlinkCards` y `computeLayout`); el problema era la acción destructiva a un clic de distancia, contra la regla §10. `ProcessViewLinkTest` (3) → suite **561/561** |
| 2026-10-14 | **T9 · N-5** asignar proceso sin modo selección | ✅ | el mismo commit — submenú **Proceso ▸** en el menú contextual de la tarjeta: radios con los procesos del tablero y **Sin proceso** (marcado el actual), que asignan **solo esa tarjeta** en **1 undo** y **sin tocar los enlaces**; mismo origen de datos que la barra de lote (`CardViewBuilder.processMenu`) para que no diverjan. `CardProcessMenuTest` (4) → suite **565/565** |
| 2026-10-14 | **T23 · B10** recordatorios programables por tarjeta (V11) | ✅ | `feat(reminders): per-card reminder calendar, notice window and card badge` (+ `docs:` del requisito/plan `c91125c` y `ff676aa` con dominio/servicio/repositorio) — `CardReminder` (un **momento**, no una regla) en `Card`/`Board`, `acknowledged` en el agregado, comandos `Add/Remove/Set/Acknowledge(+All)ReminderCommand` (1 undo el lote), `application/ReminderPlan` **puro** (`dueNow/dueOf/daily/weekly/schedule`), migración **V11** `card_reminder` con `on_time` NULL = todo el día, lectura/escritura + cascada en el repositorio, merge por id en `SyncMerge`; UI: `ui/ReminderCalendar` (puro) + `ReminderDialog` (calendario de días con ‹ ›, **Diario / Cada semana / Limpiar**, hora opcional validada), pestaña **Recordatorios** del detalle, entrada **🔔 Recordatorios…** del menú de tarjeta, **🔔 n** en el frente, banda `reminder-notice` y `ReminderNoticeWindow` (el mensaje con el título de la tarjeta: atendido / abrir / atender todo); el disparo se revisa **al arrancar y cada minuto** (`Timeline`). **Hallazgos:** ① adelantar/borrar el vencimiento dejaba recordatorios posteriores a la fecha límite → `Card.schedule` los descarta (undoable); ② re-guardar el calendario recreaba los momentos y perdía el «atendido» → `ReminderPlan.schedule` reutiliza día+hora y `Card` conserva el estado del id previo; ③ en i18n los textos con argumentos pasan por `MessageFormat` (`''` en francés **solo** en las claves con `{0}`). 43 tests nuevos (`ReminderDomainTest` 12, `ReminderCalendarTest` 9, `ReminderPlanTest` 8, `ReminderDialogTest` 8, `ReminderUiTest` 6, `SqliteReminderRoundTripTest` 3) → suite **558/558** |
| 2026-10-13 | **T16 · B8** subtareas de un nivel (V10) | ✅ | `feat(checklist): one-level subtasks (V10)` — migración **V10** (`ALTER TABLE card_checklist_item ADD COLUMN parent_id TEXT` + índice, sin FK: la integridad la lleva el agregado); `ChecklistItem.parentId` (con constructor de 3 args para no tocar llamadas previas), `Card.addChecklistItem(text, parentId)` con **un solo nivel** validado, `checklistChildrenOf`/`subtaskCount`/`subtaskTotalCount`, **borrar un padre borra sus hijos** e indentar/desindentar (`setChecklistItemParent`) en 1 undo; `MoveChecklistItemCommand` + `BoardService.moveChecklistItem`; `SetChecklistFromLines` acepta **dos espacios = subtarea**; la UI del detalle pinta subtareas indentadas con su propio menú (**Añadir subtarea**, **Sangrar**, **Quitar sangría**) y el contador ☑ cubre ambos niveles; el merge de sync viaja con `parentId`. **Hallazgos:** ① `SyncMerge` construía `ChecklistItem` a 3 args, hizo falta migrar ese punto (el campo se mergea con LWW como los demás); ② añadir un componente al record habría roto 6 llamadas en tests: se añadió un constructor de 3 args equivalente a «sin padre»; ③ una fila sin `parent_id` (formato pre-V10) se lee como ítem de primer nivel. `ChecklistHierarchyTest` (7) + `SqliteChecklistSubtaskTest` (3) → suite **512/512** |
| 2026-10-14 | **N-15** menú principal reordenado (acción por tarea) + **T21a · H-6** `instalador.bat` | ✅ | `feat(ui): reorganize the main menu and add a jpackage installer` — la barra pasa a **Archivo · Editar · Tableros · Ver · Tarjetas · Procesos · Etiquetas · Idioma · Ayuda**; **Editar** agrupa deshacer/rehacer (`Ctrl+Z`/`Ctrl+Shift+Z`), etiquetas, archivar en lote y «restablecer vistas de tarjeta»; **Ver** añade el submenú **Vista de tarjeta** (3 modos, `Ctrl+1/2/3`), el toggle **mostrar archivadas** y **modo oscuro**; **Tarjetas** sube a la barra todas las acciones del menú contextual de la tarjeta (abrir, editar, reagendar, recordatorios, proceso, archivar, exportar, transferir, borrar), reconstruidas con `setOnShowing` según la tarjeta actual (deshabilitadas sin tarjeta); **importar/exportar/PDF** y el **visor de conflictos** se mueven a **Archivo** (antes en Tableros). Los lanzadores portátiles no cambian. `instalador.bat` (jpackage: app-image sin WiX, `.exe` con WiX). `MainMenuStructureTest` (5) + `TimeReportWindowTest`/`BoardCleanupTest` actualizados → suite **589/589** (1 skip) |

---

## 4. ✅ Estado entregado (resumen; no volver a proponerlo)

| Área | Qué está hecho |
|---|---|
| Tableros y columnas | Creación con plantilla; colapso; resize; reordenar; columna «hecha»; fondo de columna (V9); badge del tablero activo |
| Tarjetas | Selección múltiple + lote (etiquetas ±, color, proceso, mover, borrar); modos de vista `Ctrl+1/2/3` + override por tarjeta; chips de etiqueta con color determinista + autocompletado; flags ★/!; ordenar por prioridad; soltar arriba/abajo; saltos de línea visibles |
| Detalle | Pestañas Descripción (markdown) / Notas / Lista de tareas / Tiempo; adjuntos; opciones avanzadas plegables |
| Notas | Separador `%%`: el markdown `#`/`##` pegado **ya no** fragmenta la lista (formato legado se lee) |
| Checklist | Por tarjeta, plano, ítems convertibles en tarjeta, progreso «☑ n/m» |
| Procesos | Procesos por tablero + filtro; precedencias con anti-ciclos (`DependencyGuard`) y «orden sugerido»; vista de diagrama (`Ctrl+Shift+2`) |
| Tiempo | Cronómetro por tarjeta (V8); **informe de tiempo** por proceso/semana con CSV/PDF (**B1**) |
| Búsqueda | **Búsqueda global** entre tableros (`Ctrl+Shift+F`, **B2**); filtro del tablero activo |
| Etiquetas | **Catálogo global** (`LabelCatalog`) con gestor; atajo **Ctrl+L**; clic en chip = filtrar |
| Apariencia | Temas claro/obscuro (`Ctrl+D`); contraste del obscuro corregido; **esquema de colores** con vista previa y «valores por defecto»; tarjeta «actual» y selección marcadas |
| Ayuda | F1 con la tabla de atajos configurables; menú Ver (tablero/proceso) |
| Persistencia | Diario de rollback (sin `-wal`/`-shm`); cierre limpio; undo/redo persistente |
| Sync | Merge 3-vías, copias de conflicto, «Sincronizar ahora», visor de copias, tests de propiedades |
| Distribución | Icono propio; launchers portables; **`instalador.bat`** (jpackage: app-image sin WiX, `.exe` con WiX); CI en GitHub Actions |
| Eliminado | Imagen de fondo de la app (S11-1) |

---

## 5. 🐞 Bugs abiertos

### 5.1 T1 — BUG-1: excepción al personalizar atajos (S11-6)

- **Síntoma:** abrir **Ayuda → Personalizar atajos de teclado** (o pulsar
  «Restaurar») puede lanzar `IllegalArgumentException`.
- **Causa (verificada):** `ui/KeyboardShortcutsDialog` usa los **nombres del
  enum** (`NEXT_CARD`) donde `application/GlobalShortcuts.Action
  .fromStorageNameOrThrow(...)` solo acepta el **nombre de almacenamiento**
  (`next`).
- **Fix:** aceptar **ambos** nombres en la fábrica y llevar el **enum** en la
  fila del diálogo en vez de re-parsear la cadena. Test que abra el diálogo para
  cada acción y para «Restaurar».

---

## 6. Detalle de las tareas T2–T12 (requisitos nuevos del 2026-10-13)

### 6.1 T2 — N-1/N-2: modo de selección

- **N-1 — Alternar barra de filtros y barra de selección.** *Hoy:*
  `BoardController` monta `selectionBar` y `filterBar` como hijos fijos del
  `root`, así que en modo selección se ven las dos. *Pedido:* alternarlas.
  *Encaje:* `rebuildSelectionBar()` ya conoce `selectionMode`; alternar
  visibilidad/`managed` de `filterBar` ahí (y al salir del modo).
- **N-2 — Esc borra la selección.** ✅ *Hecho (2026-10-13).* La tecla
  configurada de salida (`GlobalShortcuts.Action.EXIT`, Esc por defecto) actúa
  en **etapas**, que era la decisión abierta: ① si hay selección, la desmarca y
  **mantiene** el modo (para poder elegir otro conjunto); ② si no hay nada
  seleccionado, sale del modo; ③ si no está en modo selección, suelta la tarjeta
  actual (comportamiento original). `BoardController.onExitKey()`.

### 6.2 T8/T9/T10 — barra de herramientas principal

- **T8 · N-3 — Quitar el botón «agregar columna».** ✅ *Hecho (2026-10-13).*
  El botón ➕ se retiró de `buildToolbar()`; la función vive en **Tableros →
  Agregar columna** y en el menú `⋯` de **cada** columna con **añadir a la
  izquierda** / **a la derecha** (también en columnas colapsadas). El dominio
  gana `Board.addColumn(..., insertIndex)`, el comando `AddColumnCommand`
  lleva el índice y `BoardService.addColumnAt(...)` lo inserta en **un** paso
  de undo. Tests: `ColumnInsertAtTest`, `ColumnAddMenuTest`.
- **T9 · N-5 — Asignar tarjeta a un proceso sin modo selección.** *Hoy:* solo en
  lote o dentro del editor. *Pedido:* acción directa sobre la tarjeta «actual»
  (botón en toolbar o entrada de menú contextual).
- **T10 · N-4 — Copiar / pegar tarjetas.** *Hoy:* existe `copyCardToBoard`
  (otro tablero) y copiar la descripción al portapapeles; no hay copiar/pegar de
  tarjetas entre columnas del tablero actual. *Pedido:* al pegar se crean
  tarjetas nuevas en la columna seleccionada. *Encaje:* portapapeles interno
  (lista de `CardSnapshot`) o `Clipboard` del sistema con formato propio;
  reutilizar la ruta de alta/duplicado existente. **Decisión C-6:**
  ¿pegar al final de la columna o junto a la tarjeta actual?

### 6.3 T5 — N-6 + B3: filtros de vencimiento (ver §7.1 para el diseño completo)

### 6.4 T11/T12 — vista de modo proceso

- **T11 · N-7 — Nombre (y proceso actual) al crear tarjeta.** ✅ *Hecho
  (2026-10-19).* `BoardController.onProcessAddNewRelation` ya **no** inserta una
  tarjeta vacía con título fijo (`process.new.card.title`): abre el **diálogo de
  tarjeta completo** (`Dialogs.cardDialog`) con el **proceso del ancla
  preseleccionado** (`anchorCard.processId()`) y el ancla preenlazada en el lado
  correcto (predecesora/sucesora). Al aceptar se crea con **el título y la fecha
  que el usuario ponga** (más descripción, etiquetas, notas y checklist) y se abre
  el detalle. `process.new.card.title` queda sin uso.
- **T11 · N-9 — Limitar la selección a las del proceso.** ✅ *Hecho (2026-10-19).*
  `onProcessLinkExisting` usa el nuevo `BoardController.linkCandidatesFor(anchor)`:
  la lista de «elegir tarjeta existente» contiene **solo los miembros del proceso
  del ancla** (sin el ancla). Si el ancla no tiene proceso, cae a todas las
  tarjetas; un ancla desconocida no ofrece ninguna.
- **T12 · N-8 — Se borran relaciones al clic en una flecha.** ✅ *Hecho
  (2026-10-14).* **Hipótesis descartada:** verificado en el código que **no** hay
  borrado múltiple — `Board.unlinkCards` quita solo ese par y `computeLayout` se
  limita a contar los enlaces entre miembros, no los toca. **Causa real:** el
  conector de un enlace existente lleva un **área de clic invisible de 16 px**
  (`ProcessViewBuilder.connector`) y **un clic simple lo desenlazaba**, contra la
  regla ya cerrada «acciones destructivas lejos del clic accidental» (§10);
  además esa área invade el hueco de 72 px entre tarjetas, así que también
  alcanzaba a un clic cerca del borde derecho de una tarjeta. **Corrección:** el
  clic en un conector **enlazado** pide confirmación (`process.unlink.confirm`
  con los dos títulos); el conector **sugerido** (punteado) sigue enlazando con
  un clic porque no es destructivo; el tooltip lo anuncia y el área de clic lleva
  la clase `process-edge-hit` (localizable en tests). **Tests:**
  `ProcessViewLinkTest` (3) — el conector punteado enlaza de verdad con un clic
  sobre la vista renderizada, el conector enlazado expone su área de clic y su
  tooltip, y `onArrangeArrow` confirma **antes** de `unlinkCards`. Commit:
  `fix(process-view)+feat(cards): ask before unlinking and assign a process
  from the card menu (T12 · N-8, T9 · N-5)`.

### 6.5 T3 — N-10/N-11: ventana de detalle

- **N-10 — Esc = Cancelar.** ✅ *Hecho (2026-10-13).* `CardDetailWindow`
  registra un manejador de `KEY_PRESSED` **en burbuja** sobre la escena: si
  nadie consumió la tecla antes (un campo de texto, una lista de autocompletado)
  cierra la ventana igual que **Cancelar**.
- **N-11 — Reubicar los botones de nota.** ✅ *Hecho (2026-10-13).* Nuevo
  `MarkdownEditor.addHeaderButton(...)`: **Añadir nota** y **Guardar nota** van en
  la fila de cabecera del editor, junto al toggle editor/render; **«Eliminar
  nota»** se queda al pie del panel de notas.

### 6.6 T4 — N-12: filtro «NO tiene etiqueta»

✅ *Hecho (2026-10-13).* El dominio gana `LabelFilter.Mode.EXCLUDE` y la fábrica
`LabelFilter.withoutLabels()` (lista vacía + EXCLUDE = «tarjetas sin ninguna
etiqueta»); `isEmpty()` distingue «sin filtro» de ese caso. En la UI el control
de modo pasa a ser un **enum** `LabelFilterMode { ALL, ANY, NOT, NO_LABELS }`
(antes: cadenas `"AND"/"OR"`), con `StringConverter` para que solo el **texto**
se traduzca; en `NO_LABELS` el campo de etiquetas se desactiva. La barra reacciona
al **valor** del combo (no a la acción) para que un cambio programático filtre
igual. El filtro se sigue compartiendo con la vista de proceso vía
`matchesProcessFilter`.

### 6.7 T6/T7 — columnas

- **T6 · N-13 — Conservar el scroll al seleccionar.** *Hoy:* al marcar una
  tarjeta se llama a `refresh()`, que reconstruye el contenido y la columna
  **vuelve al inicio** (`vvalue` solo se usa para el scroll del tablero, no por
  columna). *Encaje:* guardar/restaurar el `vvalue` por columna alrededor del
  `refresh()` (o refrescar solo la tarjeta afectada).
- **T7 · N-14 — Elegir qué se ve en la columna, por tarjeta.** ✅ *Hecho
  (2026-10-13):* `CardViewSettings.Content` + override por tarjeta, botón en la
  tarjeta y tareas marcables desde el frente. Tests: `CardViewSettingsTest`,
  `CardContentUiTest`. Detalle original: *Hoy:*
  `CardViewSettings` tiene 3 modos **de detalle** (solo título / +3 líneas /
  completa) con override por tarjeta, pero no permite mostrar **tareas** o
  **comentarios** en el frente. *Pedido:* botón (por tarjeta) para escoger
  **1) descripción (actual) · 2) tareas (revisar/marcar) · 3) comentarios**; el
  **doble clic** sigue abriendo el detalle. *Encaje:* ampliar
  `CardViewSettings`/`CardViewBuilder` con «contenido» por tarjeta (sin
  migración). **Decisión C-7 resuelta por el usuario (2026-10-13):**
  «comentarios» = las **notas** de la tarjeta (no las entradas de tiempo).

---

## 7. Mejoras de producto (T13–T16)

### 7.1 T5 — B3: recordatorios de vencimiento (con N-6) — ✅ hecho (2026-10-13)

**Lo entregado:** `application/DueDigest` (puro: `overdue`/`dueToday`/`dueThisWeek`
con `WEEK_DAYS = 7`, hoy incluido en la semana; `matches(card, scope, today)`),
tres `ToggleButton` mutuamente excluyentes en la barra de filtros (clic en el
activo lo quita), aviso no modal **clicable** que lleva al rango más urgente y
se aparta al usarse, y submenú **Reagendar** (Hoy / Mañana / +1 semana / Próximo
lunes / Quitar fecha) en el menú de la tarjeta con un solo paso de undo
(`RescheduleCardCommand`). El aviso cuenta solo las tarjetas que el filtro de
proceso/archivadas ya muestra, así que sus números coinciden con lo que verá el
usuario. **Hallazgo:** el render de la columna aplica sus filtros en
`ColumnViewBuilder` (no en `visibleColumnsCards()`, que es solo del navegador),
así que el gate nuevo se añadió en los dos sitios; lo detectó el test de UI.

<details><summary>Diseño original (referencia)</summary>

**Decisión de diseño:** la notificación del **sistema operativo** queda fuera de
v1 (JavaFX no tiene API; `java.awt.SystemTray` falla en headless y no es
verificable en la suite). v1 usa un **recordatorio dentro de la app**:
- **Aviso discreto al arrancar** (la banda no modal `syncNotice` ya existe):
  «2 vencidas · 3 vencen hoy», clicable → activa el filtro. Nunca un modal.
- **Filtros rápidos**: **📅 Vence hoy** y **🗓 Esta semana** (próximos 7 días,
  incluido hoy), combinables con etiqueta/proceso.
- **Reagendado rápido** en el menú contextual de la tarjeta: *Hoy · Mañana ·
  +1 semana · Próximo lunes · Quitar fecha*, con **undo** propio.

**Archivos:** `application/DueDigest.java` (puro: `overdue/dueToday/
dueThisWeek`, `matches(Card, Scope{OVERDUE,TODAY,WEEK})`, reutiliza
`Card.dueDate()`/`isOverdueOn`); `application/command/RescheduleCardCommand` +
`Board.rescheduleCard`; estado `dueScope` en `BoardController` aplicado en el
pipeline de `visibleColumnsCards()` (mismo punto que etiquetas y banderas, para
que el teclado vea lo mismo), dos `ToggleButton` en `buildFilterBar()`, limpieza
en «limpiar filtros» y el aviso de arranque; entradas de reagendado en
`CardViewBuilder`.

**No cubre:** notificaciones del SO, recordatorios recurrentes, horas del día
(solo fecha), avisos periódicos con la app abierta.

**i18n:** `filter.due.today`, `filter.due.week`, `filter.due.overdue`,
`due.notice.overdue`, `due.notice.today`, `due.notice.none`, `card.reschedule`,
`card.reschedule.today`, `…tomorrow`, `…+week`, `…next.monday`, `…clear`.

**Tests:** `DueDigestTest` (rangos y límites de hoy/semana, sin fecha),
`RescheduleCardTest` (reagendar, quitar fecha, undo) y la lógica del filtro
(función pura).

**Criterio de hecho:** los filtros muestran exactamente las tarjetas esperadas;
el aviso aparece solo si hay algo vencido o para hoy y lleva al filtro al
pulsarlo.

</details>

### 7.2 T13 — B4: captura rápida con sintaxis — ✅ hecho (2026-10-13)

> **Entregado:** `application/QuickCapture.parse(raw, today) → Draft(title,
> labels, dueDate)` (puro) y `ui/QuickCaptureDialog` (campo con el foco, vista
> previa en vivo, Enter = crear, **Más opciones…** = `Dialogs.cardDialog`
> precargado). El ➕ de la columna abre la captura rápida; **Ctrl+➕**, la
> entrada «Añadir tarjeta (diálogo completo)…» del menú ⋯ de la columna y
> «Más opciones…» mantienen el diálogo completo. La previsualización se formatea
> en el diálogo (no en `QuickCapture`) porque necesita i18n y `application` no
> puede depender de `ui`. **i18n:** `quick.title`, `quick.prompt`, `quick.hint`,
> `quick.preview.labels`, `quick.preview.due`, `quick.empty`, `quick.more`,
> `quick.add`, `column.add.card.full`. **Tests:** `QuickCaptureTest`,
> `QuickCaptureDialogTest` → suite **488/488**.


| Marca | Significado | Nota |
|---|---|---|
| `+etiqueta` | añade etiqueta (`+urgente` = bandera !) | **`#` descartado a propósito**: en la app `#` identifica etiquetas de **proceso** |
| `!` | urgente (`Card.LABEL_URGENT`) | también `+urgente` |
| `*` | importante (`Card.LABEL_IMPORTANT`) | también `+importante` |
| `@hoy`, `@mañana`, `@+3d`, `@2026-10-20` | fecha de vencimiento | fecha inválida **no** se interpreta: se queda en el título |
| resto | título | los signos dentro de palabras no se tocan (`C++`, `a*b`) |

**Alcance:** `application/QuickCapture.java` (puro): `parse(raw, today)` →
`Draft(title, labels, dueDate)` + `preview(draft, i18n)`;
`ui/QuickCaptureDialog.java` con campo (foco inicial), **vista previa en vivo**,
Enter = crear y **Más opciones…** (abre `Dialogs.cardDialog` precargado). El
botón **➕** de la columna abre el diálogo rápido; el completo sigue en **➕ con
Ctrl** / «Más opciones…» / menú de columna.

**i18n:** `quick.title`, `quick.prompt`, `quick.hint`, `quick.preview`,
`quick.empty`, `quick.more`, `quick.add`.

**Tests:** `QuickCaptureTest` (título solo; varias etiquetas; `!` y `*`;
`+urgente`; fechas hoy/mañana/+3d/ISO; fecha inválida; signos dentro de
palabras; vacío → sin título; duplicados; recorte) + prueba del diálogo
(preview y «Más opciones» precargan el `CardForm`).

**Criterio de hecho:** `Llamar al banco +casa ! @mañana` + Enter crea la tarjeta
con etiqueta, bandera urgente y fecha. Commit: `feat(capture): quick card
capture with inline syntax`.

### 7.3 T14 — B5: plantillas propias + purga de archivadas — ✅ hecho (2026-10-13)

> **Entregado (con desviaciones anotadas):** `BoardTemplate` puro + menú
> **Tableros → Guardar como plantilla…**, submenú **Plantillas** (aplicar /
> eliminar) y las propias añadidas al selector de plantilla de «nuevo
> tablero»; purga **Tableros → Borrar tarjetas archivadas…** con confirmación
> por número y adjuntos a la papelera. **Desviaciones documentadas:** el
> puerto `SettingsStore` gana `remove(String)` (no existía); el color se guarda
> como **hex** porque `BoardColor` es un record, no un enum; y como el undo es
> **por tablero**, la purga trabaja tablero a tablero (un paso de deshacer en
> cada uno) restaurando al final el tablero activo. **Tests:**
> `BoardTemplateTest` (4), `ArchivedPurgeTest` (5) → suite **497/497**.


**B5.1 plantillas:** `application/BoardTemplate.java` (puro, JSON propio):
nombre + columnas (título, descripción, color, WIP, `done`) + nombres de
procesos + modo de vista. **No** guarda tarjetas. Persistencia:
`SettingsStore` con prefijo `board.template.`. `BoardService`: `templates()`,
`saveAsTemplate(name)`, `removeTemplate(name)`, `createBoardFromTemplate(...)`.
UI: **Tableros → Guardar como plantilla…**, **Tableros → Plantillas →
(aplicar/eliminar)** y las propias junto a las fijas en «nuevo tablero».
**i18n:** `template.save`, `template.save.prompt`, `template.apply`,
`template.delete`, `template.name.exists`, `template.saved`, `template.empty`.

**B5.2 purga:** **Tableros → Borrar tarjetas archivadas…**: cuenta las tarjetas
con `Card.LABEL_ARCHIVED` de **todos** los tableros, confirma con el número y
borra en **una operación deshacible** reutilizando `service.removeCards(...)`;
adjuntos huérfanos por la papelera existente.
**i18n:** `cleanup.archived`, `cleanup.archived.confirm`, `cleanup.archived.done`,
`cleanup.archived.none`.

**Tests:** `BoardTemplateTest` (round-trip JSON, sin tarjetas, aplicada a un
tablero nuevo) y `ArchivedPurgeTest` (solo borra archivadas, cuenta bien, undo).
**Commits:** `feat(templates): user-defined board templates` y
`feat(cards): purge archived cards`.

### 7.4 T15 — B7: color por etiqueta (cierra B9) — ✅ hecho (2026-10-13)

> **Entregado:** el gestor de etiquetas tiene un selector **Color** (paleta de 8
> + **Automático**) que escribe la regla `ui.label.<clave>` en claro y obscuro
> con texto por contraste; `ui/LabelChipStyle` (puro) pinta el chip con esa
> regla o, si no hay, con el hash de siempre. **B9 cerrado:** los nombres de
> color se traducen (`color.blue`…, `labels.color`, `labels.color.auto`).
> **Tests:** `LabelChipStyleTest` (5) → suite **502/502**.


**Hallazgo (verificado):** el catálogo global (`LabelCatalog` + gestor) y las
**reglas de estilo de etiqueta** (`ui.label.<clave>` con color de fondo y texto
por tema, `BoardService.setLabelStyle`/`resolvedLabelStyle`, editables en
Preferencias) **ya existen**. El hueco es doble: (1) el **chip** usa color por
hash (`chipColorClass`), no el color elegido; (2) el **gestor** no muestra ni
permite elegir color.

**Alcance:** `LabelManagerDialog`: columna de color por etiqueta (8 colores +
**Automático**) que escribe la regla de estilo para claro y obscuro;
`ui/LabelChipStyle.java` (puro): `of(label, labelRules, dark)` → estilo inline si
hay regla, o clase por hash si no; el chip lo usa (y la etiqueta del historial
★/!). **i18n:** `labels.color`, `labels.color.auto` + los nombres de color de
**B9** (`color.blue`, `color.green`, …) en los 4 bundles.

**No cubre:** renombrado propagado (ya existe) ni reordenar a mano.
**Tests:** `LabelChipStyleTest` (regla presente → color elegido; ausente → hash;
claro vs obscuro) + round-trip de la regla escrita por el gestor.
**Criterio:** elegir color y ver la etiqueta pintada así en todos los tableros.
Commit: `feat(labels): pick and persist a color per label (closes B9)`.

### 7.5 T16 — B8: subtareas jerárquicas (un nivel) — ✅ hecho (2026-10-13)

> **Entregado:** migración **V10** (`parent_id` + índice; una fila sin
> `parent_id` se lee como ítem de primer nivel), `ChecklistItem.parentId` con
> **un solo nivel** validado en el agregado, borrar un padre borra sus hijos,
> indentar/desindentar en un paso de undo (`MoveChecklistItemCommand`), **dos
> espacios = subtarea** en el formulario avanzado, y la UI del detalle con
> subtareas indentadas y menú propio. El `SyncMerge` mergea `parentId` como un
> campo más. **Reversión anotada en §8.4** (el checklist era plano por decisión
> explícita). **Tests:** `ChecklistHierarchyTest` (7),
> `SqliteChecklistSubtaskTest` (3) → suite **512/512**.


**Aviso:** el roadmap registraba el checklist **plano** por decisión explícita
del usuario; la sesión 14 lo pide, así que **se implementa** (reversión a anotar
en §8.4). Un solo nivel (sin nietos).

**Archivos:** migración **`V10__checklist_hierarchy.sql`** (última es V9):
`ALTER TABLE card_checklist_item ADD COLUMN parent_id TEXT` + índice por
`parent_id` (sin FK; la integridad la garantiza el agregado). Dominio:
`ChecklistItem.parentId` (nullable), `Card.addChecklistItem(text, parentId)`,
`checklistChildrenOf`, `subtaskCount()`, un nivel validado,
`doneChecklistCount()` cuenta padre e hijos. Comandos:
`AddChecklistItemCommand` con `parentId` y `MoveChecklistItemCommand`
(indentar/desindentar, 1 undo). Persistencia: `SqliteBoardRepository` lee/escribe
`parent_id`; `SetChecklistFromLinesCommand` acepta `dos espacios = subtarea`. UI:
`CardDetailWindow` pinta subtareas indentadas con menú ⋮ (**Añadir subtarea**,
**Convertir a tarjeta**, **Renombrar**, **Quitar**) y el contador incluye
subtareas. **i18n:** `checklist.add.subtask`, `checklist.subtask.prompt`,
`checklist.indent`, `checklist.outdent`, `checklist.subtasks.count`.

**Tests:** dominio (jerarquía, un nivel, borrar padre borra hijos, progreso,
mover + undo), repositorio (round-trip de `parent_id`, BD V9 migra a V10 sin
pérdida) y UI (hijos indentados).
**Criterio:** subtareas persistidas y sobreviven al reinicio. Commit:
`feat(checklist): one-level subtasks (V10)`.

---

### 7.6 T23 — B10: recordatorios programables por tarjeta — ✅ hecho (2026-10-14)

**Requisito (palabras del usuario, 2026-10-13):** «En tarjetas con fechas
límite, poner recordatorios. Por ejemplo, configurar fechas (y opcionalmente
horario) en el cual el programa me muestre un mensaje recordándome de la
actividad. Se deberán poder poner tantos recordatorios como se quiera… El
mensaje debe ser el mismo, por lo que convendría poner un calendario donde se
elijan las fechas desde la fecha actual hasta la fecha límite… Otra forma sería
seleccionar que se quiere diario, o una vez a la semana si la fecha límite es
varias semanas después». Requisito registrado también en
`docs/roadmap-and-decisions.md` §1.11.

> **Relación con lo ya hecho (no confundir):** **B3/T5** es **un** aviso al
> arrancar que resume las tarjetas vencidas o que vencen hoy (banda
> `due.notice` + filtros). Esto de aquí es **por tarjeta** y **programable**:
> varios momentos elegidos por el usuario.

**Decisiones de diseño (con porqué):**
1. **Un recordatorio = un momento** (`fecha` + `hora` opcional), no una regla de
   repetición en el dominio. «Diario» y «cada semana» son **generadores de la
   UI** que rellenan esa misma lista; así hay un solo modelo, la lista que se ve
   en el calendario es la verdad, y el usuario puede borrar días sueltos después
   de generarlos. *(Evita un motor de recurrencias, su undo y su merge.)*
2. **Tarjetas con fecha límite**: sin vencimiento no hay recordatorios (el
   calendario se acota a `[hoy, fecha límite]`, que es lo que pidió el usuario).
   Un recordatorio posterior a la fecha límite se rechaza al guardar.
3. **Aviso dentro de la app** (banda no modal + ventana de pendientes), decisión
   de v1 ya tomada en **B3** y registrada como **C-3**; sin `SystemTray`
   (no es verificable headless).
4. **`acknowledged` en el agregado**: un recordatorio se marca como atendido y
   no vuelve a aparecer, pero sigue en la tarjeta y en el undo/redo/export/sync
   (si fuera solo estado de UI se perdería al reiniciar y volvería a molestar).
5. **El motor de disparo es puro** (`ReminderPlan`, sin relojes): la UI le pasa
   «ahora» cada minuto (`javafx.animation.Timeline`) y al arrancar. Es lo que
   hace el aviso verificable con tests.
6. **Sin hora = todo el día**: el recordatorio dispara desde las 00:00 de su
   fecha. Cuando el usuario pide hora, el diálogo propone **09:00** (editable) y
   ofrece **Sin hora**, que es el caso más común para «recuérdame ese día».

**Archivos previstos:** dominio `CardReminder(id, LocalDate date, LocalTime time,
boolean acknowledged)` (lista ordenada en `Card`, con `reminderCount()` y el
contador de pendientes); `Card.reminders()/addReminder/removeReminder/
acknowledgeReminder` y lo mismo en `Board`; `CardSnapshot`/`BoardMemento`
ampliados (round-trip por JSON); comandos `AddReminderCommand`,
`RemoveReminderCommand`, `AcknowledgeRemindersCommand` (1 undo cada uno);
`application/ReminderPlan` (puro: `dueNow(cards, now)` → recordatorios
pendientes ya alcanzados, con la regla «sin hora = desde el inicio de su día»);
migración **V11** `card_reminder(id PK, card_id FK→card ON DELETE CASCADE,
position, remind_date TEXT, remind_time TEXT NULL, acknowledged INTEGER)` +
lectura/escritura en `SqliteBoardRepository`; UI `ui/ReminderDialog` (cuadrícula
mensual de días conmutables + ‹ › de mes + **Diario** / **Cada semana** + hora
opcional + lista de los elegidos con borrado), entrada **Recordatorios…** en el
menú de la tarjeta y 🔔 con el número en la tarjeta; banda de aviso reutilizando
el estilo de `due.notice`, clicable → abre el detalle de la tarjeta; y un
`ui/ReminderCalendar` **puro** (rejilla del mes: semanas, días fuera de rango
deshabilitados) para poder testear la cuadrícula sin JavaFX.

**i18n:** `card.reminders.menu`, `reminders.title`, `reminders.header`,
`reminders.hint`, `reminders.none`, `reminders.auto.daily`, `reminders.auto.weekly`,
`reminders.time`, `reminders.time.none`, `reminders.selected.count`,
`reminders.remove`, `reminders.clear`, `reminders.save`, `reminders.no.due`,
`reminders.after.due`, `reminders.notice.pending`, `reminders.window.title`,
`reminders.ack`, `reminders.open`, `card.reminders.badge.tip`.

**Tests previstos:** dominio (`ReminderDomainTest`: añadir/quitar/ordenar/
duplicados, marcar atendido, borrar la tarjeta se lleva sus recordatorios,
round-trip del memento, no se acepta un momento posterior al vencimiento); `ReminderPlanTest` (puro: sin hora → a partir de las 00:00; con
hora → a partir de esa hora; días pasados pendientes siguen disparando; los
atendidos no; sin fecha límite no hay recordatorios); `ReminderCalendarTest`
(rejilla del mes, rangos, cambios de mes y de año); repositorio (round-trip de
`remind_date`/`remind_time`/`acknowledged` y fila legada sin hora); y una prueba
de cableado en la UI (el menú de la tarjeta abre el diálogo y la tarjeta muestra
el 🔔).

**Criterio de hecho:** en una tarjeta con fecha límite a 10 días, elegir en el
calendario 5 días (o pulsar **Diario**), opcionalmente con hora, guardar y ver
que el 🔔 aparece en la tarjeta; al llegar ese momento la app muestra el aviso
con el título de la tarjeta, se marca como atendido al pulsarlo y no vuelve a
insistir; reiniciar no pierde ni repite los avisos. Commit:
`feat(reminders): per-card reminder schedule with a calendar`.

**Hecho (2026-10-14) — lo que salió distinto del plan y por qué:**

- **Commits:** `c91125c` (`docs: record and plan…` requisito + plan), `ff676aa`
  (dominio `CardReminder` + `Board`/`Card` + `CardSnapshot`/`BoardMemento` +
  comandos + `ReminderPlan` + migración V11 + repositorio + `SyncMerge`),
  `b890172` (UI + i18n + tests).
- **UI real:** `ui/ReminderCalendar` (puro: rejilla de mes, rango seleccionable,
  lectura de la hora) + `ui/ReminderDialog` (el **calendario** con ‹ ›, Diario /
  Cada semana / Limpiar, hora validada y contador; guarda **la lista completa** =
  1 undo) + pestaña **Recordatorios** en el detalle (calendario, **Añadir día**,
  lista con «atendido» y ✖) + entrada **🔔 Recordatorios…** en el menú de la
  tarjeta (desactivada sin fecha límite) + **🔔 n** en el frente (resaltado si
  queda alguno pendiente, gris si ya se atendieron todos) + `ui/ReminderNoticeWindow`
  (el mensaje: fila por momento con «Hoy»/fecha + hora y el **título de la
  tarjeta**, casilla de atendido, **Abrir tarjeta** y **Marcar todo como
  atendido**) + banda `reminder-notice`.
- **Disparo:** `BoardController.onStartup()` (llamado desde `Main` tras
  `stage.show()`) arranca un `Timeline` de 1 minuto y hace la primera revisión al
  instante, así que un aviso perdido con la app cerrada aparece al arrancar.
  Si hay un diálogo modal delante, la revisión espera al minuto siguiente.
- **Hallazgo ① (invariante rota):** `rescheduleCard` movía el vencimiento sin
  tocar los recordatorios, dejando momentos **posteriores** a la fecha límite
  (que el calendario ya no puede mostrar y `replaceReminders` rechazaría). Se
  corrigió en `Card.schedule`: al mover el vencimiento se descartan los momentos
  que quedan más allá, y al quitarlo se descartan todos. Undoable (es el mismo
  comando).
- **Hallazgo ② (identidad al re-guardar):** el calendario pre-marca los días que
  ya tienen recordatorio, así que guardar sin tocar nada **no** debía recrear los
  momentos (perderían su id y su estado atendido). `ReminderPlan.schedule(...)`
  reutiliza el momento existente cuando coinciden día **y** hora, y `Card`
  conserva además el estado atendido del id previo.
- **Hallazgo ③ (idiomas):** los textos con argumentos pasan por `MessageFormat`,
  donde `'` escapa: en francés hubo que escribir `''` solo en las claves con
  `{0}` (y **no** en las que se leen literalmente). `I18nCoverageTest` cubre que
  las claves existan en los 5 bundles.
- **Criterio cerrado, pero probado por partes:** el punto final (ver el aviso
  «en el momento») no es automatizable con el reloj real; se prueba lo que lo
  sostiene: `ReminderPlanTest` (8: sin hora → 00:00, con hora → su hora, un
  momento perdido sigue vencido, los atendidos no vuelven, orden, diario/semanal
  y el re-guardado), `ReminderDomainTest` (12), `ReminderCalendarTest` (9, sin
  JavaFX), `SqliteReminderRoundTripTest` (3, con fila legada sin hora y cascada),
  `ReminderDialogTest` (8, el diálogo construido **sin** mostrarlo: rejilla,
  generadores, celda fuera de rango que no entra en la selección y hora
  inválida que no guarda) y `ReminderUiTest` (6, con la UI real: el aviso al
  arrancar trae el título de la tarjeta, atender cierra la ventana, la banda y
  el 🔔 cuentan, el detalle lista los momentos y una tarjeta sin vencimiento
  explica qué falta).
- **Hallazgo ④ (para futuros tests de JavaFX):** `ToggleButton.fire()` **sí
alterna** el estado (y el handler lo ve ya alternado), pero **no hace nada en un
  botón deshabilitado**; `CheckBox.fire()` alterna y luego dispara. Los tests
  del calendario se apoyan en eso en vez de simular clics con `MouseEvent`.
- **Tests:** 43 nuevos. Suite completa: **558/558** (1 *skipped* preexistente en
  `PhpSyncApiEndpointTest`, que no corre sin servidor PHP).
  *Nota:* la suite se corre con `-Djacoco.skip=true` porque el agente de JaCoCo
  0.8.12 no instrumenta clases de JDK 25 (`Unsupported class file major version
  69`) — sigue pendiente como **T18**/**H-8**.

### 7.7 T24 — B11: tarjeta «terminada» + correcciones de vencimientos ✅

**Petición del usuario (2026-10-19):** al trabajar con vencimientos quiere un
botón «hecho» o, como mínimo, que soltar una tarjeta en la **columna
finalizada** la considere terminada. Pidió además «qué más se podría añadir,
corregir o modificar».

**Diagnóstico hecho (todo verificado en el código):** hoy **no existe** estado
de «tarjeta terminada». Lo más cercano: la etiqueta del sistema `Archivada`
(precedente documentado en `Card.java:41-47`), el flag de columna `done` —que
**solo es decorativo**, badge ✓ en `ColumnViewBuilder` y nada más— y el
checklist ☑. Además aparecieron **seis fallos reales** en la mecánica de
vencimientos (abajo, A2-A6 y B1).

**Decisiones tomadas con el usuario:** ① estado real **`completedAt`** (fecha,
migración **V12**) en vez de una etiqueta, para poder analizar retrasos;
② las hechas **se excluyen por defecto** de los filtros de vencimiento, del
aviso y de los recordatorios, con un toggle «Mostrar terminadas» que las
recupera; ③ la ronda incluye el estado **y** las correcciones, pero **no** la
vista Agenda ni el informe de ciclo (quedan para después).

#### A — Estado `completedAt` (migración V12)
- **Dominio:** `Card.completedAt` (`LocalDate`, null = pendiente) con
  `isDone()`, `complete(date)`, `reopen()`. `LocalDate` a propósito: `dueDate`
  ya es `LocalDate`, así que «entregada tarde» es
  `completedAt.isAfter(dueDate)` y el informe semanal no necesita zonas.
- **Auto-marcado:** `Board.moveCard` / `moveCardToSlot` / `moveCardsToColumn`
  completan la tarjeta si la columna destino está marcada como finalizada y la
  **reabren** al sacarla de una. El «hoy» entra como **parámetro** en los
  comandos de mover (mismo criterio que `Board.rescheduleCard`: el dominio sigue
  sin reloj y los tests son deterministas).
- **Servicio:** `BoardService.completeCard / reopenCard` →
  `CompleteCardCommand` (1 undo cada uno).
- **Persistencia:** `V12__card_completed.sql` (`ALTER TABLE card ADD COLUMN
  completed_at TEXT`), binding/lectura en `SqliteBoardRepository`, componente
  nuevo en `CardSnapshot` con **constructor retrocompatible** (el patrón de los
  recordatorios V11) y LWW en `SyncMerge`.
- **Exclusión:** un solo punto de enganche, `BoardController.matchesProcessFilter`
  (donde ya se ocultan las archivadas) ⇒ gratis para columnas, navegador,
  recuento del aviso y búsqueda; `ReminderPlan.dueNow` ignora las hechas.
- **UI:** botón ✓ en el frente de la tarjeta (patrón de ★/!), entradas en el
  menú de la tarjeta y en «Tarjetas», clase `card-done` (apagada/tachada) y
  toggle **«Mostrar terminadas»** en la barra de filtros + menú «Ver», igual
  que el de archivadas.

#### B — Seis correcciones
- **B1 · CSS del badge de vencimiento.** `.card-due` y `.card-due-overdue` se
  añaden en `CardViewBuilder` pero **no existen en ningún CSS** (`light.css` /
  `dark.css`: 0 coincidencias), así que el «rojo cuando vencida» prometido en el
  comentario **nunca se pinta**; solo cambia el tooltip. Ídem las bandas
  `due-notice`, `reminder-notice` y `sync-notice`, pintadas sin estilo.
- **B2 · Filtros ignorados en la vista de procesos.** `ProcessViewBuilder` dibuja
  `cardsOfProcess(...)` sin ningún filtro, mientras `ColumnViewBuilder` sí aplica
  etiquetas/★/!/vencimientos/archivadas: mismo tablero, dos conjuntos distintos
  (y las archivadas reaparecen).
- **B3 · El aviso de vencimientos miente.** `updateDueNotice` cuenta solo con el
  filtro de proceso/archivadas, pero su propio comentario promete «coincide
  exactamente con lo que muestran los filtros».
- **B4 · La fecha no se ve ni se edita en el detalle.** `CardDetailWindow` se
  gatea con la fecha en la pestaña de recordatorios pero no la enseña: hay que
  abrir Editar.
- **B5 · Validación inconsistente.** El DatePicker del formulario acepta fechas
  pasadas sin avisar mientras los recordatorios **rechazan** una fecha pasada.
  Se mantienen ambos comportamientos (crear una tarjeta ya vencida es legítimo)
  pero el formulario avisa en rojo.
- **B6 · Sync frágil.** ① `SyncMerge.cardKey` no incluye recordatorios ni
  timeline, así que una edición que **solo** añade un recordatorio **pierde**
  contra un borrado; ② la fecha (LWW) y los recordatorios (unión) se mezclan por
  separado y puede quedar un recordatorio **posterior** a la fecha ganadora, que
  hoy se cuela porque `Card.adoptReminder` carga sin validar.

#### C — Tests
`CompleteCardTest` (dominio: marcar/desmarcar, auto al entrar y salir de la
columna finalizada, no tocar otras columnas, undo), servicio (digesto y
recordatorios ignoran hechas, toggle «mostrar»), sync (LWW de `completedAt`,
edición solo-recordatorio ya no pierde, poda tras el merge), SQLite (ida y
vuelta de `completed_at` con y sin valor), UI (✓ cableado, clase CSS del badge,
filtros de vencimiento excluyen hechas) y `CardThemeCssTest` (los selectores
existan en ambos temas).

#### D — No cubre (queda para después)
Vista Agenda (`Board.cardsDueBy()` ya devuelve el mapa por fecha), ordenar
columna por vencimiento, reagendar en lote, «hace N días» en el badge e
informe de ciclo/entregadas-tarde. **No** se reabre la decisión cerrada de §10
sobre recordatorios sin fecha.

### 7.8 Propuesta — arreglo y rediseño de la vista de procesos (✅ A1 + B1 hechos)

> Origen: queja del usuario (2026-10-20): «apenas selecciono una tarjeta, de
> inmediato me muestra la ventana para editar sus detalles; esto no era así
> antes» y «al seleccionar una tarjeta, en la parte de abajo del contenedor
> del proceso muestran datos de la tarjeta, pero es algo confuso».
> **Decidido con el usuario (2026-10-20): A1 + B1** (más C1, ya de paso).
> Diagnóstico original conservado abajo; lo hecho, al final.

#### Diagnóstico (verificado en el código)

1. **Un clic abre el detalle.** `ProcessViewBuilder.cardNode` cuelga del
   **título** un `setOnMouseClicked → onOpenCardDetail` (línea 550) y el
   título ocupa casi toda la celda (160×34 de 180×96): cualquier clic en la
   banda central de la tarjeta abre la ventana de detalle **en el mismo
   clic** que además selecciónala (el evento burbujea al handler del nodo,
   línea 569). En el tablero kanban en cambio **clic = seleccionar** y
   **doble clic = abrir** (`CardViewBuilder`, líneas 165-184): la vista de
   procesos es la única que rompe esa convención. El handler del título
   existe desde `51b1615` (sesión «registro de tiempos»), pero con la vista
   más usada la asimetría se ha vuelto molesta.
2. **El panel inferior confuso.** Es el *inspector de relaciones*
   (`renderInspector`, líneas 703-743): al enfocar una tarjeta aparece
   **bajo su fila de proceso** una caja con: título clicable (que **también**
   abre el detalle con un clic), las listas **Predecesores / Sucresores** con
   una ✕ por enlace, **los mismos 4 botones de flecha** de la tarjeta
   repetidos como botones, y una **línea de ayuda de teclado permanente**.
   Problemas: ① duplica información que el diagrama ya dibuja con conectores;
   ② la fila **crece de golpe** al seleccionar (el panel aparece/desaparece
   y desplaza el contenido); ③ el título clicable repite el gesto incómodo
   del punto 1; ④ mezcla tres tareas (navegar, editar enlaces, enseñar
   atajos) en un bloque siempre visible; ⑤ la ayuda de teclado vive aquí en
   vez de en F1/Ayuda, que es donde vive el resto.
3. **Detalle menor relacionado:** la clase `card-current` sí se añade a la
   tarjeta enfocada del proceso, pero el CSS es `.card.card-current` y estas
   tarjetas son `.process-card`: **la tarjeta actual no se ve marcada** en
   esta vista (solo el borde azul `:focused` si tiene foco de teclado).

#### Propuestas (a decidir)

**A. Modelo de interacción del clic (arreglo del punto 1)**
- **A1 (recomendada): alinear con el tablero** — clic = seleccionar y enfocar
  la tarjeta (además de hacerla «actual»), **doble clic = abrir el detalle**;
  se elimina el `setOnMouseClicked` del título (o se le da el mismo
  comportamiento que al nodo). `Enter` ya abre el editor de la tarjeta
  enfocada, así que el teclado no cambia.
- **A2 (alternativa mínima):** conservar clic único = abrir, pero solo sobre
  un **icono/ affordance explícito** (p. ej. ✎), no sobre todo el título.

**B. Inspector inferior (arreglo del punto 2)**
- **B1 (recomendada): un solo inspector al pie de la vista completa** en vez
  de uno por fila: aparece **plegado** y se despliega al enfocar una tarjeta;
  se cierra con `Esc` o al hacer clic fuera. Contenido acotado: título de la
  tarjeta (**clic = enfocar, doble clic = abrir**), las dos listas de
  enlaces con ✕ y nada más. Se **quitan** los 4 botones duplicados (ya están
  en la tarjeta) y la línea de ayuda permanente (esa va a F1/Ayuda).
- **B2: eliminar el inspector** — los enlaces se editan solo con las flechas
  de la tarjeta y el teclado; los tooltips de los conectores ya dicen qué
  hacen. Menos UI, pero se pierde la lista «a la vista» de los enlaces de
  una tarjeta sin recorrer el diagrama.
- **B3: mantenerlo por fila pero minimalista** — solo las dos listas de
  enlaces (sin título clicable, sin botones repetidos, sin ayuda), colapsado
  de forma animada. Es el término medio, pero sigue ocupando una banda por
  fila.

**C. Coherencia y extras (opciones, prioridad baja)**
- **C1:** marcar visualmente la **tarjeta actual** en la vista de procesos
  (regla CSS `.process-card.card-current`).
- **C2:** **clic derecho** en una tarjeta del proceso = menú contextual con
  las acciones del tablero (abrir, editar, reagendar, recordatorios,
  archivar…), reutilizando las entradas del menú de la tarjeta.
- **C3:** cabecera del proceso con el **contador de tarjetas** visibles
  (`#proceso (n)`), ya que los filtros pueden ocultar parte.
- **C4:** fila de proceso vacía con acción directa («asignar tarjetas a este
  proceso») en vez del texto plano actual.

**Sin decidir todavía:** nada de esto se implementa hasta que el usuario
elija combinación (A?, B?, C?).

#### Implementado (2026-10-20: A1 + B1 + C1)

- **A1** — `ProcessViewBuilder.cardNode`: se elimina el `setOnMouseClicked` del
  título; el clic en la tarjeta ahora **selecciona + enfoca** (y la hace
  «actual») y solo el **doble clic** abre el detalle, igual que el tablero.
  `Enter` sigue abriendo el editor de la tarjeta enfocada.
- **B1** — el inspector deja de ser **uno por fila**: `buildAll` añade **una
  sola** banda `process-inspector` al pie de toda la vista (`VBox(rows,
  inspector)`); `renderInspector` la **oculta y la saca del layout**
  (`setVisible(false)/setManaged(false)`) mientras no haya tarjeta enfocada, y
  dentro solo lleva la **cabecera** (título de la tarjeta; clic = enfocar,
  doble clic = abrir) y las **dos listas de enlaces** con ✕. Se **quitan** los
  4 botones de flecha duplicados y la línea de ayuda permanente (los atajos
  siguen en `Ctrl+←/→` etc.; su texto `process.inspector.hint` se reemplaza por
  `process.inspector.caption` = «Relaciones de la tarjeta enfocada»).
- **C1** — la tarjeta «actual» se marca también aquí: CSS `.process-card.card-current`
  en claro y obscuro.
- **Tests:** `ProcessViewInteractionTest` (3: un clic no abre / doble clic abre /
  inspector único y plegado) → suite **622/622** (1 skip).

**Pendiente de discutir (no hecho):** B2/B3 (variantes del inspector) y las C2
(menú contextual), C3 (contador en la cabecera), C4 (fila vacía con acción).

#### C2 — menú contextual (💬 lluvia de ideas abierta)
Registro completo (inventario, ideas por bloques, contradicciones, sugerencias
por funcional/UX/ergonomía/usabilidad y una reconstrucción recomendada +
preguntas abiertas) en
**[`docs/process-view-context-menu.md`](docs/process-view-context-menu.md)**.
Hoy la vista de procesos **no tiene ningún menú contextual**: clic derecho no
hace nada. **No implementar hasta acordar** las preguntas abiertas de §6 de ese
documento.


---

## 8. Pendientes heredados

### 8.1 T17 — T17 robustez de datos

- **H-2 — Backup automático rotativo**: hoy una base corrupta no tiene red de
  seguridad; es el **mayor riesgo abierto** con datos reales (*Top C1*).
- **H-3 — Verificación de integridad**: `PRAGMA integrity_check` + reparación.

### 8.2 T18/T19 — ingeniería

- **H-7 — Logging persistente rotativo** (hoy solo diálogo de error).
- **H-8 — Arreglar JaCoCo** (0.8.13+ o JDK de build compatible; no instrumenta
  con JDK 25).
- **H-10 — Prueba automática del foco inicial de los diálogos** (hoy verificado
  por revisión).
- **H-11 — Ayuda F1 completa**: faltan las teclas fijas de tarjeta (flechas,
  1–9, `Alt+I/U`, `Supr`).

### 8.3 T20/T21 — sync y distribución

- **H-4**: `device_id` estable (3.2), *fractional index* (3.3), sync automático
  en segundo plano con backoff (3.4).
- **H-5**: outbox/deltas en vez de snapshot, lápidas con retención, cifrado del
  payload remoto.
- **H-6**: empaquetado nativo (`jpackage`) **hecho (T21a):** `instalador.bat`
  construye el jar y lo empaqueta (app-image sin WiX; `.exe` con WiX), con los
  launchers portátiles intactos; queda pendiente solo la **auto-actualización**
  (T21b).

### 8.4 T22 y reversiones a anotar

- **H-1**: confirmar si la **búsqueda en el tablero activo** (fase B histórica)
  se da por cerrada con **B2** (búsqueda global).
- **Reversión registrada (2026-10-13, T16 · B8):** el roadmap fijaba el
  checklist **plano** por decisión explícita del usuario; la sesión 14 pidió
  **subtareas de un nivel**, así que se implementaron (migración **V10**). La
  decisión anterior queda revertida; sigue vigente el límite de **un nivel**
  (sin nietos) y el máximo de 50 ítems por tarjeta.

### 8.5 Plan de ejecución de los pendientes (2026-10-14)

**Orden acordado con el usuario** (coste/beneficio y riesgo primero): ① **T12**
(bug abierto, barato y visible), ② **T9** (corrección pequeña), ③ **T17**
(robustez: el riesgo real con datos vivos), ④ **T10**, ⑤ **T11**, ⑥ **T18**,
⑦ **T19**, ⑧ **T22**, ⑨ **T20**, ⑩ **T21**. Las conversaciones **C-1…C-5** no se
implementan sin una decisión explícita del usuario: se preguntan al llegar a
ellas.

Reglas de siempre (§2): hexagonal, i18n en los **5** bundles, sin dependencias
nuevas, un **test que falle sin el cambio**, suite completa verde antes del
commit y cierre documentado (docs + commit + push).

#### T12 — N-8: las relaciones se borran al clic en una flecha ✅ hecho (2026-10-14)
**Causa (verificada en el código, no es un borrado múltiple):** el conector de
un enlace existente lleva un **área de clic invisible de 16 px**
(`ProcessViewBuilder.connector`) y **un clic simple lo desenlaza**
(`onArrangeArrow` → `service.unlinkCards`). El dominio borra **solo** ese par
(`Board.unlinkCards`), así que la pérdida no es masiva: el problema es que una
acción destructiva quedó **a un clic de distancia**, contra la regla ya cerrada
«acciones destructivas lejos del clic accidental» (§10). Además esa área invade
el hueco de 72 px entre tarjetas, así que un clic cerca del borde derecho de una
tarjeta también desenlaza.
**Corrección:** el clic en un conector **enlazado** pide **confirmación**
(`process.unlink.confirm` con los dos títulos); el conector **sugerido**
(punteado, no destructivo) sigue enlazando de inmediato; el tooltip dice qué va
a pasar; el camino de teclado (Ctrl+Shift+←/→) es explícito y no cambia.
**Archivos:** `ui/ProcessViewBuilder` (clase de estilo del área de clic +
tooltips), `ui/BoardController.onArrangeArrow`, i18n ×5.
**Tests:** `ProcessViewLinkTest` (UI real: un proceso sin enlaces dibuja el
conector punteado y el clic **enlaza** sin diálogo; el conector enlazado expone
su área de clic con clase y tooltip) + aserción de que `onArrangeArrow` confirma
**antes** de `unlinkCards` (mismo patrón que `KeyboardNavigationTest`).
**Criterio de hecho:** un clic accidental en una flecha no borra nada sin
preguntar, y enlazar sigue costando un clic.

#### T9 — N-5: asignar una tarjeta a un proceso sin modo selección ✅ hecho (2026-10-14)
**Alcance:** hoy solo se puede en lote (modo selección) o dentro del formulario
avanzado. Falta el gesto directo sobre la tarjeta actual. **Hecho:** submenú
**Proceso ▸** en el menú contextual de la tarjeta (radios = procesos del tablero
+ «Sin proceso», marcado el actual), un solo paso de undo y **sin tocar las
relaciones** de la tarjeta (la misma promesa que hace la barra de lote).
`CardViewBuilder.processMenu` es la única fuente de esa lista, así que el menú de
la tarjeta y la barra de lote no pueden divergir; `BoardController.onAssignProcessToCard`
hace la asignación. **Tests:** `CardProcessMenuTest` (4: etiquetas y marcado del
submenú, asignar + 1 Ctrl+Z, salir del proceso conserva los enlaces, el marcado
sigue a la tarjeta). Commit: el mismo de T12 (una sola tanda de UX sobre la misma zona).
**Diseño:** entrada **«Proceso ▸»** en el menú contextual de la tarjeta (y en el
menú de teclado de la tarjeta actual, reutilizando el diálogo de proceso que ya
usa el lote) con la lista de procesos del tablero + «Ninguno»; marcando actúa
sobre la tarjeta actual, sin entrar en modo selección. Reutiliza
`dialogs.chooseProcess`/`BulkProcessForm` y `BoardService.assignProcess`.
**Archivos:** `ui/CardViewBuilder` (menú), `ui/BoardController` (`onAssignProcessToCard`),
`ui/Dialogs` si hace falta una variante de 1 tarjeta, i18n ×5.
**Tests:** servicio (asignar sin selección = 1 undo), UI (el menú de la tarjeta
ofrece la entrada y la tarjeta queda asignada).
**Criterio:** una tarjeta de la columna se asigna a un proceso sin marcar nada.

#### N-15 — Reorganizar el menú principal ✅ hecho (2026-10-14)
**Pedido:** acciones que solo vivían en la barra de herramientas o en el menú
contextual de la tarjeta no tenían entrada en el menú de la app, y el menú
**Tableros** mezclaba asuntos de archivo con asuntos de tablero.
**Hecho:** barra = **Archivo · Editar · Tableros · Ver · Tarjetas · Procesos ·
Etiquetas · Idioma · Ayuda**.
- **Editar** (menú nuevo): deshacer (`Ctrl+Z`), rehacer (`Ctrl+Shift+Z`),
  etiquetas, archivar en lote y «restablecer vistas de tarjeta».
- **Ver**: + submenú **Vista de tarjeta** (3 modos, `Ctrl+1/2/3`), toggle
  **mostrar archivadas** y **modo oscuro** (sincronizado con el botón de la
  barra de filtros vía `setShowArchived`, que ya no puede desincronizarse).
- **Tarjetas** (menú nuevo): abrir detalle, editar, reagendar, recordatorios,
  proceso, archivar, exportar, transferir y borrar —todas las del menú
  contextual—, reconstruidas con `Menu.setOnShowing` y **deshabilitadas** si no
  hay tarjeta actual.
- **Archivo**: recibe **importar/exportar (JSON/PDF)** y el **visor de
  conflictos** (antes en Tableros).
- i18n ×5 (`menu.edit`, `menu.cards`, `menu.undo`, `menu.redo`, `menu.cardview`,
  `menu.dark.mode`, `edit.labels`, `edit.reset.cardview`, `card.open`).
**Archivos:** `ui/BoardController` (`buildMenuBar`, `rebuildEditMenu`,
`rebuildCardsMenu`, `rebuildViewMenu`, `syncViewMenu`, `setShowArchived`,
`rebuildDatabaseMenu`, `buildFilterBar`, `refresh`), `ui/CardViewBuilder`
(`rescheduleMenu` extraído), i18n ×5.
**Tests:** `MainMenuStructureTest` (5: orden de menús, agrupación de Editar,
opciones de Ver + 3 radios de vista, Tarjetas deshabilitado/habilitado por
tarjeta actual, acciones del submenú) + `TimeReportWindowTest`/`BoardCleanupTest`
actualizados → suite **589/589** (1 skip).

#### T17 — H-2 + H-3: backup automático rotativo + integridad (🟠 prioridad real)
**Alcance:** ① copia de seguridad de la BD **al arrancar** (y antes de
migraciones) en `backups/` junto a la BD, con **rotación** (N más recientes, p.
ej. 10) y sin bloquear la UI; ② `PRAGMA integrity_check` al abrir + aviso claro
si falla, con **reparación** guiada (recuperar a partir del backup más reciente
en lugar de tocar la base rota).
**Diseño:** `infrastructure/sqlite/BackupService` (puro en su política de
rotación: `Files` + orden por fecha; la copia usa la API de SQLite o copia del
archivo con la conexión cerrada) + `Database.integrity()` y un punto único en
`AppContext.openAt()` que copia, verifica y avisa. Nada de JavaFX en infra.
**Archivos:** `infrastructure/sqlite/Database`, `AppContext`, i18n ×5,
`docs/manual-usuario.md` (§18) y PLAN/roadmap.
**Tests:** `BackupRotationTest` (puro: crea N+1 copias y deja las N más nuevas,
no rota las de otro día), `SqliteIntegrityTest` (BD sana pasa; BD con la cabecera
rota falla y el aviso no rompe el arranque), y prueba de que el arranque crea la
copia con datos reales (leer el fichero copiado y contar tableros).
**Criterio:** tras arrancar hay copia en `backups/`, la política de rotación
está probada y una base corrupta no se abre en silencio.

#### T10 — N-4: copiar / pegar tarjetas ✅ *Hecho (2026-10-19)*
**Alcance entregado:** un portapapeles **interno de la app** (`CardId` en
`BoardController.cardClipboard`, no el del sistema) con **Ctrl+C/Ctrl+V**, una
entrada **Copiar tarjeta** en el menú de la tarjeta, y un botón **Pegar** en la
**cabecera de cada columna** (deshabilitado sin tarjeta copiada). Pegar crea una
**tarjeta nueva** en la columna elegida en **un** paso de undo.
**Implementación:** `application/command/DuplicateCardCommand` (copia título,
descripción, color, vencimiento, etiquetas, notas, checklist y **proceso**; **no**
copia enlaces ni recordatorios) + `BoardService.duplicateCard(CardId, ColumnId)`.
**Desvío del diseño:** el plan proponía un port `Clipboard` + `PasteCardsCommand`
con prefijo «(copia)»; se hizo **una tarjeta** por pegado, **sin** renombrar (el
usuario decide el título) y sin la capa port (no hacía falta serializar). El
undo sigue siendo **un** paso por pegado. i18n: `card.copy.menu`,
`card.paste.empty`, `column.paste.card` (×5).
**Criterio:** copiar y pegar crea una tarjeta nueva en el destino en 1 undo y sin
arrastrar dependencias.


#### T11 — N-7 + N-9: vista de proceso ✅ *Hecho (2026-10-19)*
**Alcance entregado:** ① la **flecha verde** (nueva predecesora/sucesora) abre el
**diálogo de tarjeta** (`Dialogs.cardDialog`) con el **proceso del ancla puesto**
y el ancla preenlazada, en vez de crear una tarjeta vacía «Nueva tarjeta»;
② «elegir tarjeta existente» ofrece **solo las tarjetas del proceso**.
**Archivos:** `BoardController.onProcessAddNewRelation` (diálogo reutilizando
`CardForm` con `processId` preseleccionado), `BoardController.onProcessLinkExisting`
+ nuevo `BoardController.linkCandidatesFor(CardId)` (filtro por proceso). No hizo
falta i18n nuevo (`process.new.card.title` queda huérfano).
**Tests:** `ProcessRelationDialogTest` (3) — el filtro devuelve solo los miembros
del proceso (y el ancla fuera), un ancla sin proceso cae a todas, y
`onProcessAddNewRelation` abre el diálogo, preselecciona el proceso del ancla y ya
no usa el título fijo. La creación/apertura desde el diagrama son modales, así
que su wiring se comprueba sobre el código, como en `ProcessViewLinkTest`.
**Criterio:** crear desde el diagrama produce una tarjeta con nombre y proceso, y
el selector de enlace no ofrece tarjetas de otro proceso.


#### T18 — H-7 + H-8: logging persistente y JaCoCo
**Alcance:** ① log rotativo en `logs/kanban.log` (nivel, marca de tiempo,
rotación por día y tamaño) con `java.util.logging` —sin dependencias— y volcado
del error de arranque que hoy solo se ve en un diálogo; ② **JaCoCo**: hoy el
agente 0.8.12 no instrumenta clases de JDK 25 (`Unsupported class file major
version 69`) y la suite debe correrse con `-Djacoco.skip=true`.
**Diseño H-8:** buscar en `~/.m2` una versión del plugin que soporte Java 25; si
no está (build offline), dejar `jacoco.skip` **por defecto** en el `pom.xml` con
comentario que explique cómo reactivarlo cuando haya red, y registrar el bloqueo
en el roadmap. No se silencia ningún fallo: la suite sigue corriendo entera, solo
sin instrumentación.
**Tests:** `LogRotationTest` (puro: tamaño/fecha rotan y conservan N ficheros),
cableado (un error de arranque escribe la causa raíz en el log) y comprobación de
que `mvn test` funciona sin `-Djacoco.skip`.
**Criterio:** `mvn test` corre limpio sin flags extra y cualquier fallo deja
rastro en disco.

#### T19 — H-11 + H-10: ayuda F1 completa y foco de los diálogos
**Alcance:** ① `ShortcutsHelpWindow` con **todas** las teclas de tarjeta
(flechas, 1–9, Ctrl+1/2/3, `Alt+I/U`, `Supr`, atajos reasignables) leídas de las
mismas fuentes que el manejador, para que no puedan divergir; ② prueba de que
cada diálogo abre con el foco donde debe (campo principal, no Cancelar).
**Tests:** `ShortcutsHelpCoverageTest` (toda acción reasignable aparece en la
ayuda) y `DialogFocusTest` (foco inicial por diálogo).
**Criterio:** la ayuda no miente (cobertura automática) y el foco inicial está
comprobado, no revisado a ojo.

#### T22 — H-1: cerrar la búsqueda en el tablero activo
**Alcance:** decisión, no código: la fase B histórica pedía buscar en el tablero
activo y **B2** ya trae búsqueda **global** (`Ctrl+Shift+F`, dice en qué tablero
está cada resultado). Propuesta por defecto: **cerrar H-1** con B2 y registrar la
reversión/cierre en el roadmap, dejando el filtro de etiquetas del tablero como
búsqueda dentro del tablero.
**Criterio:** el roadmap deja de listar H-1 como pendiente (o pasa a tarea real
si el usuario la quiere).

#### T20 — H-4 + H-5: sync avanzado (sesión propia)
**Alcance (grande, no cabe en un turno pequeño):** `device_id` estable (3.2),
*fractional index* para el orden (3.3), sync **automático** con backoff (3.4),
outbox/deltas en lugar de snapshot, lápidas con retención y cifrado del payload.
**Plan:** ir por partes con el diseño de `docs/sync-design.md`: (a) `device_id` +
automático con backoff (visible y barato), (b) fractional index de columnas y
tarjetas, (c) outbox/deltas + lápidas, (d) cifrado. Cada parte con su migración
(`V12+`) y su prueba de merge, y **sin romper** el formato de export JSON.
**Criterio por parte:** dos equipos siguen convergiendo y el usuario puede dejar
el sync en automático sin pensar.

#### T21 — H-6: empaquetado nativo + auto-actualización (sesión propia)
**Alcance:** `jpackage` (instalador por SO, icono, sin JRE previo) y
actualización. Requiere la toolchain (`jpackage` está en el JDK 25 local) y una
decisión de distribución (¿firma? ¿GitHub Releases?).
**Criterio mínimo:** un instalador reproducible por plataforma con los launchers
actuales intactos; la auto-actualización, solo si el usuario la quiere.
**Estado (T21a ✅ hecho):** `instalador.bat` (raíz) hace `mvn -Pportable
-DskipTests package` → stage `target\jpackage-input` → `jpackage`. Con WiX 3.x
genera un `.exe` de doble clic; **sin WiX** cae a **app-image** (`--type
app-image`), con el runtime embebido y sin Java en la máquina destino. Sale en
`target\installer\`. Usa `--main-class com.personalkanban.Launcher` y
`--java-options "--enable-native-access=ALL-UNNAMED"`; icono opcional en
`src\main\resources\icons\app.ico`. Validado de extremo a extremo (app-image).
**T21b (auto-actualización):** pendiente.

#### C-1…C-5 (conversación, no implementar sin respuesta)
C-1 import con *merge*; C-2 impresión de tableros; C-3 bandeja del sistema para
avisos; C-4 informe de tiempo (activo vs. todos); C-5 menores de H-12.

---

## 9. Prueba manual pendiente del usuario

El código está; falta que el usuario lo pruebe:

1. **Sesiones 4–10:** icono y menú Archivo; saltos de línea; atajos (F1, Ctrl+N,
   Ctrl+F, Ctrl+D, Ctrl+Q); notas y checklist; procesos y precedencias;
   undo/redo; export/import; teclado completo; resumen vs. extensa; exportar
   tarjeta; archivar/limpiar adjuntos.
2. **Sesión 11–13:** esquema de colores con vista previa; notas con markdown
   pegadas; clic = tarjeta «actual»; menú Ver y sus atajos; selección visible;
   tooltips; tema obscuro.
3. **Sesión 14:** informe de tiempo (CSV/PDF) y búsqueda global
   (`Ctrl+Shift+F`).
4. **Sesión 15 (T23 · B10, recordatorios):** añadir un par de días en el
   calendario de una tarjeta con fecha límite (y **Diario** en otra), dejar la
   app abierta con un recordatorio **a un minuto vista** y comprobar que salta
   la ventana con el título; marcar «atendido», reiniciar y ver que **no
   vuelve**; adelantar el vencimiento de esa tarjeta y ver que los
   recordatorios posteriores desaparecen (undoable).

---

## 10. 📚 Documentación del proyecto (lo que queda)

| Archivo | Qué contiene |
|---|---|
| [`README.md`](README.md) (raíz) | README público: features, requisitos, compilar, portable, datos |
| [`docs/README.md`](docs/README.md) | Índice de la documentación interna |
| **[`PLAN.md`](PLAN.md)** *(este)* | Plan, checklist, estado y bitácora |
| [`docs/project-overview.md`](docs/project-overview.md) | Qué es la app, stack, estructura, launchers |
| [`docs/architecture.md`](docs/architecture.md) | Arquitectura hexagonal, capas, esquema SQLite, patrones |
| [`docs/manual-usuario.md`](docs/manual-usuario.md) | Manual de usuario completo |
| [`docs/sync-design.md`](docs/sync-design.md) | Diseño de la sincronización (modelo, backends, fases) |
| [`docs/import-merge-tableros.md`](docs/import-merge-tableros.md) | Propuesta pendiente de conversación (C-1) |
| [`docs/roadmap-and-decisions.md`](docs/roadmap-and-decisions.md) | Requisitos históricos, hallazgos verificados y decisiones con su porqué |

**Decisiones de diseño ya cerradas** (no re-debatir): `#` = etiqueta de
**proceso**; etiquetas **case-insensitive** con predefinidas protegidas
(Urgente/Importante/Archivada); notas con separador **`%%`**; atajos
**reasignables** = los que actúan sobre la tarjeta actual; acciones destructivas
**lejos** del clic accidental; i18n en **5** bundles; arquitectura hexagonal;
sin migraciones sin necesidad.
