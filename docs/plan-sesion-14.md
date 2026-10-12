# Plan — Sesión 14: mejoras B1–B5 / B7 / B8 (2026-10-12)

> Pedido del usuario: *«B1–B5/B7/B8 (informe de tiempo, búsqueda global,
> recordatorios, captura rápida con sintaxis, plantillas propias, registro de
> etiquetas, subtareas)»*, y **ejecutarlas**, actualizando documentación y
> haciendo **commit + push** al terminar cada una.
>
> Origen de las propuestas: [`mejoras-propuestas.md`](mejoras-propuestas.md)
> §B. Estado verificado hecho/pendiente:
> [`pending-mejoras-propuestas.md`](pending-mejoras-propuestas.md).

## Reglas comunes a todas las mejoras

1. **Arquitectura hexagonal** (`ArchitectureTest`): la lógica pura y testeable
   va en `domain/` o `application/`; `ui/` solo orquesta JavaFX. Nada de
   `javafx.*` fuera de `ui/` (y de `DependencyGuard`, que ya existe).
2. **Sin dependencias nuevas** (el proyecto compila offline) ni cambios de
   build salvo que una mejora lo exija.
3. **i18n en los 4 bundles** (`messages.properties` base + `_en`/`_es`/`_de`/`_fr`);
   `I18nCoverageTest` es el juez.
4. **Tests nuevos por mejora** (mínimo: lógica pura + una prueba de que la UI
   está cableada) y suite completa verde antes del commit.
5. **Una mejora = un commit** en inglés (`feat(...)`), con push, como en las
   sesiones 10–13. Documentación (`PLAN.md`, `docs/*`) actualizada en el mismo
   commit o en el inmediatamente anterior.
6. **Checklist de prueba manual** actualizado al final de cada mejora.

## Orden de ejecución y criterio de prioridad

Se respeta el orden pedido (B1 → B2 → B3 → B4 → B5 → B7 → B8), que coincide con
valor/riesgo: primero lo que **explotan datos que ya existen** (B1, B2), luego
lo que añade **hábito diario** (B3, B4), después **estructura reutilizable**
(B5, B7) y por último lo que **exige migración de esquema** (B8).

| # | Mejora | Coste | Migración | Riesgo | Prioridad original |
|---|---|---|---|---|---|
| B1 | Informe de tiempo | medio | no | bajo | 🔴 Alta |
| B2 | Búsqueda global | medio | no | bajo | 🟠 Media-alta |
| B3 | Recordatorios de vencimiento | medio | no | medio | 🟠 Media |
| B4 | Captura rápida con sintaxis | bajo | no | bajo | 🟠 Media |
| B5 | Plantillas propias + purga de archivadas | medio-alto | no | medio | 🟡 Media |
| B7 | Registro de etiquetas (color elegido) | medio | no | medio | 🔵 Baja |
| B8 | Subtareas jerárquicas | alto | **sí (V10)** | alto | 🔵 Baja |

---

## B1 — Informe de tiempo (horas por proceso y por semana, export CSV/PDF)

**Qué se quiere.** Las horas ya se registran por tarjeta (pestaña **Tiempo**,
`Timeline`/`TimelineEntry`). Falta *explotarlas*: ver cuánto tiempo se fue en
cada proceso y en cada semana, y poder sacarlo del programa.

**Alcance:**
- Agregado **puro y testeable** de las entradas de tiempo del tablero activo:
  - por **proceso** (incluidas las horas de tarjetas sin proceso → fila
    `Sin proceso`), y
  - por **semana ISO**, con un rango configurable (por defecto las **4 últimas
    semanas**, incluida la actual).
  - Una entrada **en curso** cuenta con su duración hasta «ahora» (mismo
    criterio que `TimelineEntry.durationMillis(now)`).
- **Ventana** (no modal) con las dos tablas, la fila de **total** y las horas en
  formato `h:mm` (y minutos crudos donde ayude).
- **Export**: CSV y PDF. El PDF reutiliza `CardPdfWriter.write(text, target)`,
  que ya escribe texto paginado: se genera el informe en texto y se le pasa.
- Entrada de menú **Ver → Informe de tiempo…**.

**Qué NO cubre:** informes de otros tableros a la vez, gráficos, filtros por
etiqueta dentro del informe, ni rango arbitrario por fechas (queda como
`en semana`/`rango de semanas`).

**Diseño y archivos:**
- `application/TimeReport.java` — inmutable: `TimeReport.of(Board, Instant now,
  ZoneId, int weeks)`, con `byProcess()` (`Row(name, millis, cardCount)`),
  `byWeek()` (`Row(label, millis)`), `total()`. Sin JavaFX.
- `ui/TimeReportWindow.java` — `TableView`s + combo de rango + botones
  **Exportar CSV…** / **Exportar PDF…**.
- `ui/TimeReportCsv.java` — render CSV (separador `;`, cabecera, escapado de
  comillas/`;`/saltos) para poder abrirlo en Excel en español.
- `BoardController`: item `report.time` en el menú **Ver** + `onShowTimeReport()`
  + `Dialogs` para el destino de guardado (precedente `onExportBoardPdf`).
- i18n: `report.time`, `report.title`, `report.by.process`,
  `report.by.week`, `report.total`, `report.weeks`, `report.empty`,
  `report.export.csv`, `report.export.pdf`, `report.no.process`,
  `report.export.done`.

**Tests:** `TimeReportTest` (agrupación por proceso y por semana, entrada
abierta, tarjeta sin proceso, rango de semanas, total = suma de filas,
ignora entradas vacías) y `TimeReportCsvTest` (cabecera, escapado, formato de
horas, filas vacías).

**Criterio de hecho:** ventana abierta desde el menú, números verificados en un
test con datos construidos a mano, export CSV/PDF escrito en disco en la prueba
manual, suite verde, i18n completa, commit `feat(report): time report by process
and week with CSV/PDF export`.

---

## B2 — Búsqueda global (Ctrl+Shift+F y botón 🔍)

**Hallazgo que cambia el coste (verificado en código):** el caso de uso ya
**existe**: `BoardService.findCards(BoardId, query)` busca sin distinguir
mayúsculas en **título, descripción, notas, etiquetas y checklist**,
funciona sobre **cualquier tablero del catálogo sin cambiar el tablero activo**
(hidrata un agregado privado) y ya tiene **6 tests**
(`BoardServiceTest`). Falta únicamente (a) recorrer todos los tableros con
resultados y (b) la UI.

**Alcance:**
- `application/GlobalSearch.java`: consulta `boards()` y llama a `findCards`
  por tablero; devuelve `List<Hit(boardId, boardName, card, snippet)>`
  agrupados por tablero y **con límite** por tablero (por defecto 20) para que
  un tablero enorme no ahogue el resultado. Query vacía → sin resultados.
- `ui/GlobalSearchDialog.java`: campo de búsqueda (foco inicial), lista de
  resultados «Tablero › Título» + fragmento con el texto encontrado,
  contador «N resultados», y **Enter/doble clic abre el tablero y deja la
  tarjeta focalizada** (método nuevo `BoardController.focusCardInBoard(BoardId,
  CardId)`; si la tarjeta está oculta por el filtro activo, se limpia el filtro
  antes de enfocar, como hace el atajo de conflicto).
- Entradas: **Ctrl+Shift+F**, menú **Ver → Buscar en todos los tableros…** y
  botón 🔍 en la barra de herramientas (la búsqueda es de uso frecuente).
- i18n: `search.title`, `search.prompt`, `search.empty`, `search.results`,
  `search.open`, `search.menu`, `search.tooltip`, `search.hint`.

**Qué NO cubre:** búsqueda incremental en segundo plano con indexación,
búsqueda por fecha/color/estado, o buscar dentro del contenido de los adjuntos.

**Tests:** `GlobalSearchTest` (cruza tableros, respeta el límite, no cambia el
tablero activo, query vacía/`null`, `snippet` correcto) + prueba de cableado
(la clase de UI usa `GlobalSearch` y el atajo está registrado).

**Criterio de hecho:** Ctrl+Shift+F abre el diálogo, buscar encuentra tarjetas
de otro tablero y abrirlas lleva a su tablero con la tarjeta focalizada; commit
`feat(search): global search across boards with Ctrl+Shift+F`.

---

## B3 — Recordatorios de vencimiento + vistas «vence hoy / esta semana»

**Decisión de diseño (se documenta por qué):** la notificación del **sistema
operativo** (bandeja) queda **fuera de v1**. JavaFX no tiene API de
notificaciones; hacerlo exige `java.awt.SystemTray`, que falla en
entornos headless, requiere icono propio y añade riesgo al jar portable sin
poder probarse en la suite. v1 usa un **recordatorio dentro de la app**, que sí
es verificable:
- **Aviso discreto al arrancar** (la banda no modal `syncNotice` ya existe):
  «2 vencidas · 3 vencen hoy» clicable → activa el filtro correspondiente.
  Nunca es un modal (regla de diseño §4.1 del sync: no interrumpir).
- **Dos filtros rápidos** en la barra de filtros: **📅 Vence hoy** y
  **🗓 Esta semana** (próximos 7 días, incluido hoy), combinables con el filtro
  de etiquetas/proceso ya existente.
- **Reagendado rápido** en el menú contextual de la tarjeta: *Hoy · Mañana ·
  +1 semana · Próximo lunes · Quitar fecha*, con **undo** propio
  (`RescheduleCardCommand`, un memento por cambio).

**Diseño y archivos:**
- `application/DueDigest.java` (puro): `DueDigest.of(List<Card>, LocalDate
  today)` → `overdue`, `dueToday`, `dueThisWeek`, `total()`, más `matches(Card,
  Scope)` con `Scope {OVERDUE, TODAY, WEEK}`. Reutiliza `Card.dueDate()` y
  `Card.isOverdueOn(today)`.
- `application/command/RescheduleCardCommand.java` + `Board.rescheduleCard(...)`.
- `BoardController`: estado `dueScope` (null / OVERDUE / TODAY / WEEK) aplicado
  en el pipeline de `visibleColumnsCards()` (mismo punto que los filtros de
  etiqueta y banderas → el teclado y las flechas siguen viendo exactamente lo
  mismo), dos `ToggleButton` en `buildFilterBar()`, limpieza en «limpiar
  filtros», y el aviso de arranque.
- `ui/CardViewBuilder`: entradas del menú contextual de reagendado.
- i18n: `filter.due.today`, `filter.due.week`, `filter.due.overdue`,
  `due.notice.overdue`, `due.notice.today`, `due.notice.none`,
  `card.reschedule`, `card.reschedule.today`, `...tomorrow`, `...+week`,
  `...next.monday`, `...clear`.

**Qué NO cubre:** notificaciones del SO/bandeja, recordatorios recurrentes,
horas del día (solo fecha), ni avisos periódicos mientras la app está abierta
(queda como posible extensión con `Timeline`).

**Tests:** `DueDigestTest` (rangos, límites exactos de hoy/semana, sin fecha),
`RescheduleCardTest` (reagendar, quitar fecha, undo) y un test de la lógica del
filtro por vencimiento (función pura extraída).

**Criterio de hecho:** los dos filtros muestran exactamente las tarjetas
esperadas, el aviso aparece solo si hay algo vencido o para hoy y lleva al
filtro al pulsarlo; commit `feat(due): due-date reminders, today/week views and
quick reschedule`.

---

## B4 — Captura rápida con sintaxis

**Sintaxis decidida** (se documenta la desviación respecto de la propuesta
original, que usaba `#`):

| Marca | Significado | Nota |
|---|---|---|
| `+etiqueta` | añade etiqueta (`+urgente` = bandera !) | **`#` descartado a propósito**: en esta app `#` es la convención de las **etiquetas de proceso** (`LabelConventions`), y usarlo para etiquetas de tarjeta crearía colisiones con el filtro de proceso |
| `!` | urgente (`Card.LABEL_URGENT`) | también `+urgente` |
| `*` | importante (`Card.LABEL_IMPORTANT`) | también `+importante` |
| `@hoy`, `@mañana`, `@+3d`, `@2026-10-20` | fecha de vencimiento | fechas inválidas **no** se interpretan: se quedan en el título, sin romper nada |
| resto | título | los signos dentro de palabras no se tocan (`C++`, `a*b`) |

**Alcance:**
- `application/QuickCapture.java` (puro y sin JavaFX): `QuickCapture.parse(raw,
  today)` → `Draft(title, labels, relevantes, dueDate)` + `preview(draft, i18n)`
  para enseñar en vivo lo que se va a crear.
- `ui/QuickCaptureDialog.java`: un campo (foco inicial), **vista previa en vivo**
  del parseo, Enter = crear, y botón **Más opciones…** que abre el diálogo
  completo (`Dialogs.cardDialog`) con los campos ya parseados precargados.
- Punto de entrada: el botón **➕** de la cabecera de columna abre el diálogo
  rápido (hoy abre el completo); el diálogo completo sigue accesible desde
  **➕ con Ctrl** / «Más opciones…» y desde el menú de la columna. *Motivo: ➕ es
  el gesto más frecuente del tablero y ahí es donde duele abrir un modal de 9
  campos.*
- i18n: `quick.title`, `quick.prompt`, `quick.hint`, `quick.preview`,
  `quick.empty`, `quick.more`, `quick.add`.

**Tests:** `QuickCaptureTest` (título solo; varias etiquetas; `!` y `*`;
`+urgente`; fechas `hoy`/`mañana`/`+3d`/ISO; fecha inválida se queda en el
título; signos dentro de palabras; texto vacío o solo marcas → sin título;
etiquetas duplicadas; recorte de espacios) y una prueba del diálogo (preview y
«Más opciones» precargan el `CardForm`).

**Criterio de hecho:** escribir `Llamar al banco +casa ! @mañana` y pulsar Enter
crea la tarjeta con la etiqueta, la bandera urgente y la fecha correctas; commit
`feat(capture): quick card capture with inline syntax`.

---

## B5 — Plantillas propias + purga de tarjetas archivadas

Son dos cosas que la propuesta agrupa; se implementan en **dos commits** dentro
de la misma mejora para que el undo/purga no quede mezclado con las plantillas.

### B5.1 — Plantillas propias guardables

- `application/BoardTemplate.java` (puro, JSON propio como `CardViewSettings`):
  nombre + lista de columnas (título, descripción, color, WIP, `done`) +
  nombres de procesos + modo de vista. **No** guarda tarjetas.
- Persistencia: `SettingsStore` con prefijo `board.template.` (no hace falta
  puerto nuevo; las plantillas son preferencias, no datos de negocio).
- `BoardService`: `templates()`, `saveAsTemplate(name)` (captura del tablero
  activo), `removeTemplate(name)`, `createBoardFromTemplate(name, template)`.
- UI: **Tableros → Guardar como plantilla…**, **Tableros → Plantillas →
  (aplicar/eliminar)** y, en el diálogo de nuevo tablero, las plantillas
  propias aparecen junto a las fijas (Estándar / Personalizadas / Sin columnas).
- i18n: `template.save`, `template.save.prompt`, `template.apply`,
  `template.delete`, `template.name.exists`, `template.saved`, `template.empty`.

### B5.2 — Purga de archivadas

- **Tableros → Borrar tarjetas archivadas…**: cuenta las tarjetas con etiqueta
  `Card.LABEL_ARCHIVED` de **todos** los tableros, pide confirmación con el
  número, y borra en **una sola operación deshacible** reutilizando
  `service.removeCards(...)` (ya existe y ya deja undo). Los adjuntos que
  queden huérfanos se limpian con la papelera existente (`cleanup.attachments`).
- i18n: `cleanup.archived`, `cleanup.archived.confirm`, `cleanup.archived.done`,
  `cleanup.archived.none`.

**Tests:** `BoardTemplateTest` (round-trip JSON, plantilla sin tarjetas,
aplicada a un tablero nuevo → mismas columnas/procesos) y
`ArchivedPurgeTest` (solo borra las archivadas, cuenta bien, undo las restaura).

**Criterio de hecho:** crear un tablero desde una plantilla propia reproduce la
estructura; borrar archivadas deja intactas las demás y se puede deshacer;
commits `feat(templates): user-defined board templates` y
`feat(cards): purge archived cards`.

---

## B7 — Registro de etiquetas con color elegido

**Hallazgo (verificado):** el catálogo global (`LabelCatalog` + gestor) y las
**reglas de estilo de etiqueta** (`ui.label.<clave>` con color de fondo y texto
por tema, `BoardService.setLabelStyle` / `resolvedLabelStyle`, editables hoy en
Preferencias) **ya existen**. El hueco real es doble:
1. el **chip** de la tarjeta usa color por hash (`chipColorClass`), no el color
   elegido por el usuario; y
2. el **gestor de etiquetas** no muestra ni permite elegir color.

**Alcance:**
- `LabelManagerDialog`: columna de color por etiqueta (los 8 colores del tema +
  **Automático**), que escribe la regla de estilo para claro y obscuro.
- `ui/LabelChipStyle.java` (puro): `LabelChipStyle.of(label, labelRules, dark)`
  → estilo inline si hay regla, o la clase de color por hash si no. El chip pasa
  a usarlo, y la etiqueta del historial de banderas ★/! también queda coherente.
- i18n: `labels.color`, `labels.color.auto`, y los nombres de color de B9 — **se
  aprovecha para cerrar B9** (nombres de color traducidos en el selector), que
  es barato y comparte sitio: `color.blue`, `color.green`, … en los 4 bundles.

**Qué NO cubre:** renombrado propagado (ya lo hace el catálogo desde la sesión
12), ni reordenar etiquetas a mano (orden alfabético actual).

**Tests:** `LabelChipStyleTest` (regla presente → color elegido; ausente →
hash; claro vs obscuro) y round-trip de la regla escrita por el gestor.

**Criterio de hecho:** elegir color para una etiqueta en el gestor y ver esa
etiqueta pintada con ese color en las tarjetas de todos los tableros; commit
`feat(labels): pick and persist a color per label (closes B9)`.

---

## B8 — Subtareas jerárquicas (lo último, exige migración)

**Aviso de decisión revertida:** el roadmap registraba el checklist **plano** por
decisión explícita del usuario (*«no quiero que llegue a ser jerárquica por lo
pronto»*). La Sesión 14 lo pide, así que **se implementa**, y la reversión se
anota en [`roadmap-and-decisions.md`](roadmap-and-decisions.md).

**Alcance (un solo nivel, a propósito):** tareas y **subtareas** de esa tarea.
Sin nietos: es lo que cubre el 95 % del uso real y evita recursión en dominio,
UI e indentación.

**Diseño y archivos:**
- Migración **`V10__checklist_hierarchy.sql`** (la última es V9):
  `ALTER TABLE card_checklist_item ADD COLUMN parent_id TEXT` + índice por
  `parent_id`. Sin FK (SQLite + `ALTER TABLE` limitado); la integridad la
  garantiza el agregado, como con el resto de referencias.
- Dominio: `ChecklistItem` gana `parentId` (nullable) y `ChecklistItem` sigue
  siendo inmutable; `Card` gana `addChecklistItem(text, parentId)`,
  `checklistChildrenOf(itemId)`, `subtaskCount()`, y **un solo nivel validado**
  (un hijo no puede ser padre). `Card.doneChecklistCount()` cuenta padre e
  hijos (y un padre con subtareas pendientes no puede marcarse hecho → se
  calcula, no se bloquea la UI con un error).
- Comandos: `AddChecklistItemCommand` con `parentId`, y
  `MoveChecklistItemCommand` (indentar/desindentar, una sola operación undo).
- Persistencia: `SqliteBoardRepository` guarda/lee `parent_id`; el
  `SetChecklistFromLinesCommand` acepta `dos espacios = subtarea` para no perder
  el flujo rápido por líneas.
- UI: `CardDetailWindow` pinta las subtareas indentadas con su propia caja y
  menú ⋮ (**Añadir subtarea**, **Convertir a tarjeta**, **Renombrar**, **Quitar**);
  el contador del checklist muestra `hechas/total` incluyendo subtareas.
- i18n: `checklist.add.subtask`, `checklist.subtask.prompt`,
  `checklist.indent`, `checklist.outdent`, `checklist.subtasks.count`.

**Tests:** dominio (jerarquía, un nivel, borrar padre borra hijos, progreso
incluye subtareas, mover indentar/desindentar + undo), repositorio SQLite
(round-trip de `parent_id`, base creada con V9 migra a V10 sin pérdida) y UI
(los hijos se pintan indentados).

**Criterio de hecho:** una tarjeta puede tener subtareas indentadas, se
persisten, sobreviven al reinicio, y convertirlas en tarjeta sigue funcionando;
commit `feat(checklist): one-level subtasks (V10)`.

---

## Riesgos y decisiones a revisar con el usuario

| Punto | Decisión tomada | Cómo se cambia si el usuario prefiere otra cosa |
|---|---|---|
| `#` en la captura rápida | Descatalogado a favor de `+etiqueta`, porque `#` ya identifica etiquetas de **proceso** | Una línea en `QuickCapture`: aceptar `#` como alias |
| Notificación de vencimiento | Dentro de la app (banda + filtros), no del sistema operativo | Añadir `SystemTray` opcional detrás de una preferencia |
| Subtareas | Un solo nivel (sin nietos) | Hacer recursivo `parentId` (ya queda en el modelo) |
| Informe de tiempo | Solo tablero activo, 4 semanas por defecto | Añadir «todos los tableros» y rango por fechas |

## Checklist de la sesión

- [ ] B1 — Informe de tiempo
- [ ] B2 — Búsqueda global
- [ ] B3 — Recordatorios de vencimiento
- [ ] B4 — Captura rápida con sintaxis
- [ ] B5 — Plantillas propias + purga de archivadas
- [ ] B7 — Registro de etiquetas con color (incluye B9)
- [ ] B8 — Subtareas jerárquicas (V10)
- [ ] `PLAN.md`, `manual-usuario.md` y `pending-mejoras-propuestas.md` al día
- [ ] Suite completa verde al cerrar cada mejora

## Referencias

- Propuestas: [`mejoras-propuestas.md`](mejoras-propuestas.md) §B
- Estado verificado: [`pending-mejoras-propuestas.md`](pending-mejoras-propuestas.md)
- Arquitectura y dónde va cada cosa: [`architecture.md`](architecture.md)
- Roadmap vivo: [`../PLAN.md`](../PLAN.md) y [`plan-sesiones.md`](plan-sesiones.md)
