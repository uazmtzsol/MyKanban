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
> **Estado (2026-10-13):** rama `ImprovementsV1`; suite **453/453 OK** (1 skip:
> endpoint PHP sin servidor); migraciones hasta **V9**. Documentación
> consolidada, **T1–T5 hechos** (fase 1 y fase 2 completas); siguiente: **T6**
> (scroll de columna, §2).

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

- [ ] **T6 · N-13** ⬜ **Conservar la posición de scroll** de la columna al
  seleccionar una tarjeta. §6.7.
- [ ] **T7 · N-14** ⬜ **Botón por tarjeta** para elegir qué ver en la columna:
  descripción / tareas / comentarios. §6.7.
- [ ] **T8 · N-3** ⬜ **Quitar el botón «agregar columna»** de la toolbar; la
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

- [ ] **T13 · B4** ⬜ **Captura rápida con sintaxis** al dar de alta. §7.2.
- [ ] **T14 · B5** ⬜ **Plantillas propias** + **purga de archivadas**. §7.3.
- [ ] **T15 · B7** ⬜ **Color por etiqueta** elegido en el gestor (cierra **B9**,
  nombres de color traducidos). §7.4.
- [ ] **T16 · B8** ⬜ **Subtareas jerárquicas** de un nivel (migración **V10**).
  §7.5.

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
- [ ] 💬 **C-3** Vencimientos: ¿banda interna (actual) o bandeja del sistema?
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
5. **Una tarea = un commit** en inglés, con **push**, y la **bitácora** (§3)
   actualizada en el mismo commit.
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
| T17 | H-2/H-3 backup + integridad | 🟠 Media | no | medio |
| T18 | H-7/H-8 logging + JaCoCo | 🟡 Media | no | bajo |
| T19 | H-11/H-10 ayuda + foco | 🔵 Baja | no | bajo |
| T20 | H-4/H-5 sync avanzado | 🔵 Baja | **sí** | alto |
| T21 | H-6 jpackage | 🔵 Baja | no | medio |
| T22 | H-1 confirmar fase B | — | no | — |

El detalle de alcance/archivos/tests de **T1–T12** está en §6 y en las entradas
por tarea; el de **T13–T16** (B4–B8) en §7; el resto en §8.

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
| 2026-10-13 | **T5 · N-6/B3** vencimientos | ✅ | `feat(due): overdue/today/week filters, startup reminder and quick reschedule` — `DueDigest` (puro: rangos y conteos), `Board.rescheduleCard` + `RescheduleCardCommand` (1 undo), tres toggles mutuamente excluyentes en la barra, aviso no modal clicable y submenú «Reagendar» en la tarjeta. **Hallazgo:** el filtro de la barra solo existía en `visibleColumnsCards()` (navegador); el render de la columna aplica sus filtros en `ColumnViewBuilder`, así que el de vencimientos se añadió también ahí (test de UI lo pilló). `DueDigestTest` (5), `RescheduleCardTest` (4), `DueFilterUiTest` (3) → suite **453/453** |

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

- **T8 · N-3 — Quitar el botón «agregar columna».** *Hoy:* `BoardController`
  (~línea 699) crea `addColumn` (➕) en la toolbar. *Pedido:* quitarlo; la
  función debe estar en el menú **Tableros** (comprobar que ya existe) **y** en
  cada columna, con botón **añadir a la izquierda** y **a la derecha**.
  *Encaje:* `ColumnViewBuilder` ya tiene menú `⋯` por columna; añadir dos
  entradas reutilizando `service.addColumn` + reordenar.
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
- **T7 · N-14 — Elegir qué se ve en la columna, por tarjeta.** *Hoy:*
  `CardViewSettings` tiene 3 modos **de detalle** (solo título / +3 líneas /
  completa) con override por tarjeta, pero no permite mostrar **tareas** o
  **comentarios** en el frente. *Pedido:* botón (por tarjeta) para escoger
  **1) descripción (actual) · 2) tareas (revisar/marcar) · 3) comentarios**; el
  **doble clic** sigue abriendo el detalle. *Encaje:* ampliar
  `CardViewSettings`/`CardViewBuilder` con «contenido» por tarjeta (sin
  migración). **Decisión C-7:** «comentarios» = ¿notas o comentarios de tiempo?

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

### 7.2 T13 — B4: captura rápida con sintaxis

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

### 7.3 T14 — B5: plantillas propias + purga de archivadas

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

### 7.4 T15 — B7: color por etiqueta (cierra B9)

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

### 7.5 T16 — B8: subtareas jerárquicas (un nivel)

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
- Anotar la **reversión** de la decisión «checklist plano» (T16) y registrar las
  decisiones nuevas de §6/§7 en el historial.

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
