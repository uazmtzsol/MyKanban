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
> **Estado (2026-10-13):** rama `ImprovementsV1`; suite **455/455 OK** (1 skip:
> endpoint PHP sin servidor); migraciones hasta **V10**. Documentación
> consolidada, **T1–T6 hechos** (fases 1–2 y el scroll de columna); siguiente:
> **T7** (qué muestra cada tarjeta, §2/§6.7).

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

- [ ] **T9 · N-5** 🔄 **Asignar una tarjeta a un proceso sin modo selección**
  (hoy solo en lote o dentro del editor). §6.2.
- [ ] **T10 · N-4** ⬜ Botón **copiar / pegar tarjetas** (pega como tarjetas
  nuevas en la columna actual). §6.2.

### Fase 5 — Vista de proceso

- [ ] **T11 · N-7 + N-9** ⬜ Vista de proceso: **nombre (y proceso) al crear
  tarjeta**; **limitar la selección** a las tarjetas del proceso. §6.4.
- [ ] **T12 · N-8** 🐞 **Se borran las relaciones** al hacer clic en una flecha
  de la vista de proyectos: investigar y corregir. §6.4.

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

### Fase 7 — Robustez y distribución

- [ ] **T17 · H-2 + H-3** ⬜ **Backup automático rotativo** de la BD +
  **verificación de integridad** (`PRAGMA integrity_check`) con reparación. §8.1.
- [ ] **T18 · H-7 + H-8** ⬜ **Logging persistente rotativo** y arreglo de
  **JaCoCo** (no instrumenta con JDK 25). §8.2.
- [ ] **T19 · H-11 + H-10** ⬜ **Ayuda F1 completa** (teclas fijas de tarjeta) y
  prueba automática del **foco inicial de los diálogos**. §8.2.
- [ ] **T20 · H-4 + H-5** ⬜ Sync: `device_id` (3.2), *fractional index* (3.3),
  sync automático (3.4), outbox/deltas, lápidas, cifrado. §8.3.
- [ ] **T21 · H-6** ⬜ **Empaquetado nativo** (`jpackage`) + auto-actualización.
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
| T17 | H-2/H-3 backup + integridad | 🟠 Media | no | medio |
| T18 | H-7/H-8 logging + JaCoCo | 🟡 Media | no | bajo |
| T19 | H-11/H-10 ayuda + foco | 🔵 Baja | no | bajo |
| T20 | H-4/H-5 sync avanzado | 🔵 Baja | **sí** | alto |
| T21 | H-6 jpackage | 🔵 Baja | no | medio |
| T22 | H-1 confirmar fase B | — | no | — |

El detalle de alcance/archivos/tests de **T1–T12** está en §6 y en las entradas
por tarea; el de **T13–T16** (B4–B8) y **T23** (B10) en §7; el resto en §8.

---

## 3. 📓 Bitácora de avance

> Cada fila = una tarea cerrada, con su commit. Aquí se ve **dónde se quedó**.
> La tarea en curso se marca como «🔄 en curso» al empezarla.

| Fecha | Tarea | Estado | Commit / notas |
|---|---|---|---|
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
| 2026-10-14 | **T23 · B10** recordatorios programables por tarjeta (V11) | ✅ | `feat(reminders): per-card reminder calendar, notice window and card badge` (+ `docs:` del requisito/plan `c91125c` y `ff676aa` con dominio/servicio/repositorio) — `CardReminder` (un **momento**, no una regla) en `Card`/`Board`, `acknowledged` en el agregado, comandos `Add/Remove/Set/Acknowledge(+All)ReminderCommand` (1 undo el lote), `application/ReminderPlan` **puro** (`dueNow/dueOf/daily/weekly/schedule`), migración **V11** `card_reminder` con `on_time` NULL = todo el día, lectura/escritura + cascada en el repositorio, merge por id en `SyncMerge`; UI: `ui/ReminderCalendar` (puro) + `ReminderDialog` (calendario de días con ‹ ›, **Diario / Cada semana / Limpiar**, hora opcional validada), pestaña **Recordatorios** del detalle, entrada **🔔 Recordatorios…** del menú de tarjeta, **🔔 n** en el frente, banda `reminder-notice` y `ReminderNoticeWindow` (el mensaje con el título de la tarjeta: atendido / abrir / atender todo); el disparo se revisa **al arrancar y cada minuto** (`Timeline`). **Hallazgos:** ① adelantar/borrar el vencimiento dejaba recordatorios posteriores a la fecha límite → `Card.schedule` los descarta (undoable); ② re-guardar el calendario recreaba los momentos y perdía el «atendido» → `ReminderPlan.schedule` reutiliza día+hora y `Card` conserva el estado del id previo; ③ en i18n los textos con argumentos pasan por `MessageFormat` (`''` en francés **solo** en las claves con `{0}`). 43 tests nuevos (`ReminderDomainTest` 12, `ReminderCalendarTest` 9, `ReminderPlanTest` 8, `ReminderDialogTest` 8, `ReminderUiTest` 6, `SqliteReminderRoundTripTest` 3) → suite **558/558** |
| 2026-10-13 | **T16 · B8** subtareas de un nivel (V10) | ✅ | `feat(checklist): one-level subtasks (V10)` — migración **V10** (`ALTER TABLE card_checklist_item ADD COLUMN parent_id TEXT` + índice, sin FK: la integridad la lleva el agregado); `ChecklistItem.parentId` (con constructor de 3 args para no tocar llamadas previas), `Card.addChecklistItem(text, parentId)` con **un solo nivel** validado, `checklistChildrenOf`/`subtaskCount`/`subtaskTotalCount`, **borrar un padre borra sus hijos** e indentar/desindentar (`setChecklistItemParent`) en 1 undo; `MoveChecklistItemCommand` + `BoardService.moveChecklistItem`; `SetChecklistFromLines` acepta **dos espacios = subtarea**; la UI del detalle pinta subtareas indentadas con su propio menú (**Añadir subtarea**, **Sangrar**, **Quitar sangría**) y el contador ☑ cubre ambos niveles; el merge de sync viaja con `parentId`. **Hallazgos:** ① `SyncMerge` construía `ChecklistItem` a 3 args, hizo falta migrar ese punto (el campo se mergea con LWW como los demás); ② añadir un componente al record habría roto 6 llamadas en tests: se añadió un constructor de 3 args equivalente a «sin padre»; ③ una fila sin `parent_id` (formato pre-V10) se lee como ítem de primer nivel. `ChecklistHierarchyTest` (7) + `SqliteChecklistSubtaskTest` (3) → suite **512/512** |

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
| Distribución | Icono propio; launchers portables; CI en GitHub Actions |
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

- **T11 · N-7 — Nombre (y proceso actual) al crear tarjeta.** *Hoy:*
  `BoardController.onProcessAddNewRelation` crea la tarjeta con **título fijo**
  (`process.new.card.title`) sin descripción y abre el detalle. *Pedido:* poder
  poner **primero el nombre**, luego la descripción, y **asignar el proceso
  actual**.
- **T11 · N-9 — Limitar la selección a las del proceso.** *Hoy:* «elegir tarjeta
  existente» (`onProcessLinkExisting`) ofrece **todas**. *Pedido:* solo las del
  proceso actual.
- **T12 · N-8 — Se borran relaciones al clic en una flecha.** 🐞 *Hipótesis a
  comprobar:* `onArrangeArrow`/`onProcessLinkExisting` recrean los enlaces del
  proceso y pierden los que no están en la nueva relación; o el refresco
  recalcula el grafo y descarta enlaces fuera de la columna visible. Verificar
  antes de tocar `ProcessViewBuilder`.

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
- **H-6**: empaquetado nativo (`jpackage`) + auto-actualización; hoy es fat jar +
  `.bat`/`.sh`.

### 8.4 T22 y reversiones a anotar

- **H-1**: confirmar si la **búsqueda en el tablero activo** (fase B histórica)
  se da por cerrada con **B2** (búsqueda global).
- **Reversión registrada (2026-10-13, T16 · B8):** el roadmap fijaba el
  checklist **plano** por decisión explícita del usuario; la sesión 14 pidió
  **subtareas de un nivel**, así que se implementaron (migración **V10**). La
  decisión anterior queda revertida; sigue vigente el límite de **un nivel**
  (sin nietos) y el máximo de 50 ítems por tarjeta.

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
