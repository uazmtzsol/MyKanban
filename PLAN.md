# PLAN — Roadmap de trabajo por sesiones

> Documento **vivo**: se actualiza en cada sesión (estado, hallazgos nuevos,
> checklists). El contexto estable del proyecto vive en `docs/`
> (ver `docs/README.md`). El usuario elige qué fase ejecutar en cada sesión.

*Última actualización: 2026-09-29 · Sesión 3 en curso: 5 correcciones/mejoras
UI pedidas por el usuario (ver checklist abajo).*

---

## Sesión 3 — Correcciones y mejoras de UI (pedidas por el usuario)

- [x] **3.1 — Asa de resize en la última columna.** `ColumnViewBuilder.buildAll`
  ahora añade un handle también DESPUÉS de la última columna (antes solo
  había entre columnas), simétrico al existente.
- [x] **3.2 — Quitar botón "Vaciar tablero"** de la toolbar (🗑, entre añadir
  columna y deshacer): eliminado botón + handler + `BoardService.clearBoard()`
  + `ClearBoardCommand` + `Board.clearColumns()` (sin otro uso) + i18n
  `toolbar.clear.board`/`confirm.clear.board` (4 bundles). El test de
  independencia export/import que usaba `clearBoard()` como mutación de
  prueba ahora usa `removeCard`. 130/130 tests OK.
- [x] **3.3 — Botón "copiar todo" en el editor markdown** de comentarios
  (`CardDetailWindow`): botón ⧉ junto al **?**, copia `editor.getText()` al
  portapapeles del sistema; deshabilitado cuando el editor está vacío.
  i18n `card.md.copy.tip` en los 4 bundles. 130/130 tests OK.
- [ ] **3.4 — Toggles ★ Importante / ! Urgente en la barra de filtro:**
  filtran tarjetas por esas etiquetas; con ambos activos exige las dos
  (AND), combinado con el filtro de texto existente.
- [ ] **3.5 — Exportar el tablero visible a PDF:** snapshot visual paginado
  (Apache PDFBox), accesible desde botón de toolbar y menú Board.

---

## Estado general

| Fase | Contenido | Estado |
|---|---|---|
| **P0 — Correcciones bloqueantes** | Bug guardado color custom · textos i18n editor markdown · rollback en memoria · PLAN/docs | 🔄 Código hecho + tests OK; falta prueba manual |
| **P1.5 — Selección múltiple** | Botón selección por columna · acciones bulk: etiquetas ±, color, mover, borrar · 'vaciar columna' al menú | 🔄 Código hecho + tests OK; falta prueba manual |
| **P1 — Etiquetas** | Separadores espacio/coma ✅ · dedup case-insensitive ✅ · almacenamiento robusto ✅ · chips con color ✅ · autocompletado ✅ · flags ★/! ✅ | ✅ Código completo; falta prueba manual |
| **P2 — Vista de tarjetas** | 3 modos (título / título+resumen / completa) · global por tablero + override individual · persistencia | 🔄 Código hecho + tests OK; falta prueba manual |
| **P3 — Deseable (futuro)** | Impresión · registro de etiquetas · clic en chip = filtrar | ⬜ Bloqueada hasta que el usuario la pida |

Leyenda: ⬜ pendiente · 🔄 en curso · ✅ hecha y probada por el usuario

---

## Fase P0 — Correcciones bloqueantes

Objetivo: que el usuario pueda usar colores personalizados y los diálogos
muestren textos correctos, con la garantía de que la UI nunca diverge de la BD.

> Nota: las menciones a "9 bundles" en esta sección son históricas (P0 se
> hizo antes de reducir i18n a 4 idiomas, más adelante en la misma sesión).
> El estado VIGENTE es: 4 bundles (EN/ES/DE/FR).

### Tareas
- [x] **P0.1 — Guardado de color custom.** `BoardColor.stored()` (hex si es
  custom, nombre si es preset) y usarlo en `SqliteBoardRepository.bindColumn`
  y `bindCard`. La lectura ya soporta ambos (`fromStored`).
- [x] **P0.2 — Rollback en memoria si el persist falla.** En `BoardService`:
  push al historial solo tras `persist()` exitoso; si falla, restaurar el
  memento *before* antes de relanzar. Así el modelo nunca queda divergido.
- [x] **P0.3 — i18n:** claves `dialog.ok` / `dialog.cancel` en los 9 bundles.
- [x] **P0.4 — Test de cobertura i18n:** todas las claves usadas en `ui`
  existen en los 9 bundles (evita futuros textos=clave).
- [x] **P0.5 — Diálogo de error con causa raíz:** `Dialogs.describeFailure`
  usado por `guarded()` y `switchDatabase`.
- [x] **P0.6 — Tests:** round-trip SQLite con color custom; rollback de
  modelo + historial limpio al fallar persist; undo fallido es reintentable.
- [x] **P0.7 — Suite completa: 77 tests, 0 fallos (BUILD SUCCESS).**
- [x] **P0.8 — Documentación actualizada** (docs/ + PLAN.md).

### Checklist de prueba manual (usuario)
1. Crear tarjeta y elegir un color **custom** en el ColorPicker → OK →
   el color se ve en el tablero, sin error.
2. Reiniciar la app → el color sigue.
3. Editar una tarjeta existente de paleta → cambiar a custom → idem.
4. Doble clic en tarjeta → ventana markdown → botones dicen "OK"/"Cancelar"
   (o traducción) en el idioma activo; probar 2 idiomas.
5. Cambiar idioma → los botones siguen bien.
6. (Regresión) Mover tarjetas, undo/redo, cambiar color de columna.

---

## Fase P1 — Etiquetas: parseo, normalización y visual

Objetivo: etiquetas fáciles de escribir, visualmente claras, con
autocompletado estándar.

### Tareas
- [ ] **P1.1 — Separadores opcionales:** `Dialogs.parseLabels` divide por
  `[, \t，]+` (coma, espacio, tab, coma CJK). Cubre tarjeta Y filtro (mismo
  método). Casos: `et1 et2 et3` / `et1,et2,et3` / `et1,   et2  et3` ⇒ 3.
- [ ] **P1.2 — Dedup case-insensitive:** en `Card.setLabels`, "UAZ" y "uaz"
  son una etiqueta (se conserva la primera grafía escrita). El filtro ya era
  case-insensitive; ahora el dominio es consistente.
- [ ] **P1.3 — Almacenamiento robusto:** separador `;` → `\u001F` en
  `SqliteBoardRepository`; lectura retrocompatible con el formato `;`.
- [ ] **P1.4 — Chips con color:** `.card-label-chip` en ambos CSS: fondo
  redondeado, padding; color determinista por hash de la etiqueta sobre una
  paleta de 8 pares fondo/texto legibles (light y dark).
  `FlowPane` en vez de `HBox` para varias etiquetas.
- [ ] **P1.5 — Autocompletado:** `LabelSuggester` (lógica pura, en
  application o domain) + componente UI reutilizable (TextField + popup
  ListView). Comportamiento: sugerencias por prefijo case-insensitive,
  ↑/↓ navega, Enter/Tab selecciona, Esc cierra, clic con ratón selecciona,
  y siempre se puede seguir escribiendo una etiqueta nueva. Fuente v1:
  etiquetas del tablero activo + las de la tarjeta.
- [ ] **P1.6 — Textos i18n:** prompts nuevos ("separadas por espacios o
  comas") en los 4 bundles (EN/ES/DE/FR).
- [ ] **P1.7 — Tests:** `LabelSuggester` (prefijo, vacío, case), parseo de
  separadores, dedup case, round-trip con `\u001F` y con etiqueta que
  contiene `;`.
- [ ] Decidir con el usuario (opcional): límite `MAX_LABELS=8` — mantener,
  ampliar o mostrar aviso visible en vez de excepción.

### Checklist de prueba manual (usuario)
1. Etiquetas en los 3 formatos → 3 chips ("et1", "et2", "et3").
2. Escribir `uaz` en una tarjeta y `UAZ` en otra → filtro "uaz" encuentra ambas.
3. Chips con colores distintos y legibles en tema claro y oscuro.
4. En el campo etiquetas escribir `pr` → aparecen sugerencias; seleccionar
   con ratón; con teclado (↑ ↓ Enter); con Tab; Esc cierra; escribir una
   etiqueta nueva sin elegir sugerencia también funciona.
5. (Regresión) Filtro AND/OR de la barra sigue funcionando con espacios.

---

## Fase P2 — Modos de vista de tarjeta

Objetivo: tableros compactos a voluntad, con control global e individual.

### Tareas
- [ ] **P2.1 — Modelo:** `CardViewMode { TITLE_ONLY, TITLE_PREVIEW, FULL }` +
  política de "modo efectivo" (override ?? default del tablero) como lógica
  pura testeable.
- [ ] **P2.2 — Render:** `MarkdownSummary.render` paramétrico
  (PREVIEW=3 líneas, FULL=completo nativo; nota: tablas/imágenes completas
  solo en la ventana de detalle).
- [ ] **P2.3 — Global por tablero:** menú de botón en la toolbar (radio de 3
  opciones) + atajos `Ctrl+1` / `Ctrl+2` / `Ctrl+3`.
- [ ] **P2.4 — Individual:** menú contextual en la tarjeta: "Solo título /
  Título+resumen / Completa / Usar el del tablero" (limpia override).
- [ ] **P2.5 — Persistencia:** JSON en `SettingsStore` clave
  `ui.cardview.<boardId>` (default + overrides); default inicial PREVIEW.
- [ ] **P2.6 — Tests:** política de modo efectivo, persistencia de
  preferencias, i18n de los nuevos textos (4 bundles).

### Checklist de prueba manual (usuario)
1. `Ctrl+2` → todas las tarjetas muestran título+3 renglones.
2. `Ctrl+1` → todo colapsa a solo título; `Ctrl+3` → texto completo.
3. Menú contextual de UNA tarjeta → "Completa" → solo esa cambia; el resto
   sigue en el modo del tablero.
4. Menú contextual → "Usar el del tablero" → la tarjeta vuelve al modo global.
5. Reiniciar → preferencias conservadas.
6. (Regresión) Drag & drop y doble clic a detalle funcionan en los 3 modos.

---

## Fase P3 — Deseable / futuro (NO implementar hasta que el usuario lo pida)

- **Impresión de tableros.** Alternativas a evaluar entonces:
  - A) `PrinterJob` sobre snapshot del nodo del tablero (rápido, fidelidad de pantalla, paginación pobre).
  - B) Export HTML estilizado (reutiliza commonmark; pagina mejor; abre/imprime en navegador). Recomendación preliminar: B, con A como vista rápida.
- **Registro de etiquetas** (migración V5 tabla `label`): colores elegidos
  por el usuario, listado global para autocompletado entre tableros,
  renombrado propagado.
- **Clic en chip = filtrar** el tablero por esa etiqueta.
- **Higiene menor:** `deleteBoard` parametrizado, import duplicado en
  `ColumnViewBuilder`, aviso por límite de etiquetas.

---

## Notas de sesión

### Sesión 1 — 2026-09-29
- Diagnóstico completo con causas raíz (ver `docs/roadmap-and-decisions.md` §2).
- Hipótesis del batch JDBC vacío descartada empíricamente (jshell).
- Creada `docs/` (README, project-overview, architecture, roadmap-and-decisions)
  y este PLAN. Sin cambios de código todavía.
- **Próximo paso acordado:** el usuario elige fase (P0 recomendada).

### Sesión 2 — 2026-09-29 (Fase P0 implementada)
- **P0.1** `BoardColor.stored()` + repositorio usa `stored()` en columnas y
  tarjetas. Fix del "Could not save board default-board" con colores custom.
- **P0.2** `BoardService.finishTransaction` persiste ANTES de push al
  historial y hace rollback del agregado si persist falla; `undo` usa
  peek→persist→pop (la entrada sobrevive a un fallo, reintento posible);
  `redo` igual: push al historial solo tras persist exitoso.
- **P0.3** `dialog.ok`/`dialog.cancel` en los 9 bundles (ES="Aceptar",
  HI="ठीक है", ZH="确定", etc.).
- **P0.4** `I18nCoverageTest`: escanea `i18n.text("...")` en ui y verifica
  contra los 9 bundles + ancla de regresión para los botones.
- **P0.5** `Dialogs.describeFailure(RuntimeException)` (mensaje + causa raíz
  más profunda) usado en `guarded()` y `switchDatabase`.
- **P0.6/7** Tests nuevos: `storedValueIsNeverNullForPaletteAndCustomColors`,
  `saveThenLoadRoundTripsCustomColors`,
  `failedSaveRollsBackModelAndLeavesUndoHistoryClean`,
  `failedUndoKeepsCurrentStateAndHistoryEntry`. Suite: 77/77 OK.
- Nota de entorno: el repo local de Maven no tenía JavaFX; con red disponible
  `mvn test` descargó todo y funciona.
- **Siguiente:** checklist de prueba manual de P0 (arriba) por parte del
  usuario; luego elegimos P1.
- **i18n reducido** a pedido del usuario: eliminados bundles ru/hi/zh/ja;
  `I18n.SUPPORTED` = en/fr/es/de; `I18nCoverageTest` y docs actualizados.
  Un setting viejo `ui.language=ru` cae a inglés sin errores.

### Sesión 2b — 2026-09-29 (P1.5: Selección múltiple, pedido del usuario)
- Requisito nuevo del usuario: quitar el botón 🧽 "eliminar todas las
  tarjetas" del header (peligroso), pasarlo al menú de la columna; en su
  lugar un botón ☐ "Selección de tarjetas" con acciones masivas.
- **Dominio:** `Board.addLabels/removeLabels` (dedup case-insensitive
  conservando primera grafía), `recolorCards`, `removeCards`,
  `moveCardsToColumn` (atómico: valida todo antes de mutar;
  `WipLimitBulkException.excess()` dice cuántas no caben; conserva el orden
  de selección; ignora las que ya están en el destino).
  `WipLimitExceededException` ahora expone `limit()`.
- **Application:** comandos `AddLabels/RemoveLabels/RecolorCards/RemoveCards/
  MoveCards` + casos de uso en `BoardService` (undo/redo en 1 paso, heredado
  del memento).
- **UI:** header de columna = [＋][☐][⋯]; menú ⋯ incluye ahora "Eliminar
  todas las tarjetas". Barra de selección (toolbar azul bajo la toolbar)
  con ✕ salir, 🏷 etiquetas ±, 🎨 color, ➡ mover, 🗑 eliminar y contador
  "N seleccionada(s)"; acciones deshabilitadas con 0 seleccionadas. Clic
  simple sobre tarjeta = alternar selección (sin botones editar/borrar);
  doble clic sigue abriendo el detalle fuera del modo selección. CSS
  `.card-selectable` (borde punteado) y `.card-selected` en ambos temas.
- **Dialogs:** `parseLabels` ahora acepta espacios O comas mezclados (parte
  de P1). Diálogos bulk: etiquetas (añadir/quitar + campo), color
  (ColorPicker), mover (ComboBox de columnas destino).
- **i18n:** claves `bulk.*` y `column.select.cards` en EN/ES/DE/FR. ES:
  botón ☐="Selección de tarjetas".
- **Tests:** `BulkCardOperationsTest` (8), bulk en `BoardServiceTest` (3,
  incl. atomicidad WIP y undo en un paso), `DialogsParseLabelsTest` (5).
  Suite: 93/93 OK.
- **Decisión:** el modo selección es por columna (requisito del usuario:
  "seleccionar tarjetas de una sola columna"); cambiar a otra columna sale
  y entra al modo en la nueva. El movimiento bulk va al FINAL de la columna
  destino, en el orden de selección.

### Sesión 2c — 2026-09-29 (Columnas al crear un tablero, pedido del usuario)
- Requisito: al crear un tablero, preguntar cuántas columnas tendrá O sus
  nombres separados por comas. `"4"` → Columna 1..4 (nombre localizado);
  `"Por hacer, Haciendo, Hecho"` → 3 columnas con esos nombres.
- **Ampliación (mismo día):** elección de plantilla ANTES del detalle —
  **Plantilla Kanban Estándar** (preseleccionada: Por hacer / Haciendo /
  Hecho, nombres localizados), **Personalizadas** (flujo del párrafo
  anterior) o **Sin columnas**. Cancelar en cualquier diálogo no crea nada.
- **Implementación:** un solo prompt (tras el nombre del tablero) que acepta
  ambas formas. Parser puro `Dialogs.parseNewBoardColumns(raw,
  defaultName)` — número ⇒ nombres por defecto vía i18n `board.column.default`
  ("Columna {0}"); texto ⇒ split SOLO por comas (los títulos pueden contener
  espacios); número fuera de 1..12 ⇒ null (mensaje de validación y NO crea
  el tablero); vacío ⇒ tablero sin columnas (como antes). Confirmación final:
  'Tablero "X" creado con N columna(s)'.
- **UI:** `BoardController.onNewBoard` crea tablero + columnas semilla
  (`service.addColumn` con color por defecto, WIP ilimitado), una transacción
  por columna (cada una deshacible individualmente).
- **i18n:** `board.columns.header/prompt/invalid/created`, `board.column.default`
  en EN/ES/DE/FR.
- **Tests:** `ParseNewBoardColumnsTest` (7): número, espacios, nombres,
  whitespace/empties, fuera de rango, vacío, función de nombre. 100/100 OK.
- **Nombre del tablero visible (mismo día, petición del usuario):** creó
  "Doctorado" y no veía el nombre en ninguna parte. Ahora hay un badge
  `.board-name` en la toolbar (junto a "Personal Kanban") con el nombre del
  tablero activo, y el título de la ventana es
  "Personal Kanban — <tablero> — <archivo.bd>". Se actualiza vía
  `updateBoardIdentity()` desde `rebuildAll()` (cubre abrir/crear/renombrar/
  eliminar/importar/idioma/cambio de BD).

### Sesión 2d — 2026-09-29 (P1.6: personalización visual, pedido del usuario)
- Requisito: fondo de color para columnas y tarjetas, colapsar columnas
  (solo título) y visualización más moderna de tarjetas.
- **Tinte de superficie (`ColorCss.applySurface`):** columnas y tarjetas
  pintan FONDO con el color elegido mezclado (12% claro / 22% oscuro sobre
  el color de superficie del tema) + borde exacto si es custom. El color
  por defecto NO tinta (tablero calmado hasta que el usuario elija).
  Reemplaza a `applyAccent` (deprecado). Motivo del tinte: el color crudo
  del picker satura/oscurece y rompe la legibilidad.
- **Colapsar columnas:** botón « en el header y opción en el menú ⋯; la
  columna colapsada es una tira vertical de 52px con título rotado -90°,
  contador de tarjetas y botón ». Se persiste POR TABLERO en `app_setting`
  clave `ui.columnstate.<boardId>` (ids separados por ";", vacío = ninguno).
  API: `BoardService.collapsedColumnsOf/setCollapsedColumns`; estado en
  `BoardController.collapsedColumns`, recargado en `rebuildAll`.
- **CSS moderno de tarjetas:** radio 10, padding 10, sombra suave y hover
  con borde+sombra mayor (light y dark).
- **Hallazgo/fix (✔️):** `listBoards` ordenaba por `created_at, name`; con
  created_at en ms, dos tableros del mismo milisegundo empataban y el
  nombre decidía el orden (un test lo delató al dejar de coincidir). Ahora
  `ORDER BY created_at, id` — determinista.
- **Fix cartel <noscript> (mismo día):** la vista previa markdown mostraba
  "JavaScript is disabled for safety." — la plantilla de `Markdown.java`
  incluía un `<noscript>` que un WebKit/WebView muestra VISIBLE cuando el
  JS está desactivado (justamente nuestro caso). Eliminado el elemento; la
  seguridad real sigue siendo `setJavaScriptEnabled(false)` en
  `CardDetailWindow`. Firmas de `toStyledDocument` sin cambio para
  llamadores (placeholder interno menos).
- **Guía rápida markdown (mismo día, pedido del usuario):** botón **?** en
  la barra inferior del editor de tarjetas → ventana emergente no-modal
  (640×560, resizable, ligada a la del editor) con tabla "Escribe →
  Resultado": encabezados, negrita, cursiva, tachado, código, listas con
  viñetas y numeradas, cita, línea horizontal, enlace y tabla GFM. La
  columna derecha se renderiza con EL MISMO motor commonmark → la guía no
  puede divergir de lo que el editor soporta. Etiquetas localizadas en
  EN/ES/DE/FR (nueva familia de claves `card.md.help.*` + tip actualizado);
  palabra de muestra localizada (ES "Texto").
- **Memoria de carpeta export/import (mismo día, pedido del usuario):** el
  FileChooser abría en "Este equipo" en cada uso. Ahora `AppContext`
  recuerda la última carpeta usada en `config.properties` (clave
  `io.lastdir`, máquina-local, COMPARTIDA por export e import — ambos son
  "dónde viven mis archivos de tablero"). El chooser abre ahí; si la
  carpeta no existe ya (USB retirada) o nunca se memorizó, el fallback es
  la carpeta personal del usuario (`user.home`) en TODOS los OS, Windows
  incluido — decisión confirmada con el usuario: es un directorio real
  siempre navegable, a diferencia de "Este equipo" que es ubicación virtual
  sin ruta de filesystem. La memoria se revalida en cada lectura y nunca
  se guarda una carpeta inexistente. `writeConfiguredDatabase` conserva la
  clave al reescribir el config en cambios de BD.
- Suite: 108/108 OK.

### Sesión 2e — 2026-09-29 (Flags Urgente/Importante + aclaración de expectativas)
- **IMPORTANTE (lección):** el usuario ejecutó el portable y "no vio las
  mejoras". Dos causas: (a) autocompletado y chips con color NO estaban
  implementados aún (son P1, no confundir con el parseo ya hecho), y (b)
  probó el jar viejo antes de la regeneración. Contramedidas: SELLO DE
  COMPILACIÓN visible en el título de la ventana (`AppVersion.stamp()` →
  [v2d-<fecha>-<hora>] desde version.properties) y LEEME que lista qué
  está y qué NO está implementado.
- **Flags rápidos (pedido del usuario):** ★ = Importante, ! = Urgente.
  Botones junto al título de CADA tarjeta: gris atenuado si no está la
  etiqueta, vivo (★ ámbar #f59e0b, ! rojo #dc2626; dark: #fbbf24/#f87171)
  si está. Dominio: `Card.LABEL_URGENT/LABEL_IMPORTANT` (etiquetas normales
  del set — filtro, bulk y futuro color de chips las incluyen),
  `hasLabelIgnoreCase`, `Board.toggleLabel` (case-insensitive, respeta
  tope de 8, NotFoundException si no existe la tarjeta). Comando
  `ToggleLabelCommand` + `BoardService.toggleCardLabel` (1 transacción,
  undo con Ctrl+Z). Ocultos en modo selección (el bulk ya cubre).
- Suite: 110/110 OK. Portable regenerado: jar con sello [v2d-20260929-1907]
  verificado dentro del jar (version.properties) + smoke test OK.
### Sesión 2f — 2026-09-29 (Fase P1 completa: autocompletado + chips)
- **LabelSuggester (domain, puro):** token actual relativo al caret (split
  por `[,​\s，]+`), sugerencias por prefijo case-insensitive preservando la
  grafía original, excluye etiquetas ya presentes y el token mismo;
  `apply()` reemplaza el token y añade espacio final. 9 tests (incl. el
  ejemplo del usuario: "pr" → pri/primer/primo/principal).
- **LabelAutoComplete (ui):** popup ListView bajo el campo (debounce 120ms).
  ↑/↓ navegan, Enter/Tab aplican, Esc cierra, clic aplica; nunca bloquea
  escribir (no es constraint). `vocabularySupplier` dinámico para el filtro
  (el vocabulario cambia mientras vive el campo). En cardDialog excluye
  las etiquetas ya presentes de la tarjeta.
- **Vocabulario:** `BoardService.labelVocabulary()` = Importante, Urgente,
  luego todas las del tablero activo (primera grafía, orden de aparición).
  Conectado a: cardDialog (añadir/editar), bulkLabelsDialog y el FILTRO de
  la barra.
- **Chips con color:** FlowPane (envuelven) + clase determinista por hash
  (`chip-color-0..7`): 8 pares fondo/texto pastel legibles en light y
  dark, estilo GitHub. Misma etiqueta = mismo color siempre.
- **Almacenamiento \u001F:** separador de labels en SQLite cambia de ';' a
  unit-separator; lectura retrocompatible con filas legacy. Test: etiqueta
  "et;iqueta" sobrevive + fila legacy 'a;b;c' lee bien.
- Suite: 120/120 OK. Portable regenerado (sello [v2d-20260929-1933]) +
  smoke test OK.
### Sesión 2g — 2026-09-29 (Fase P2: modos de vista de tarjeta)
- **Modelo puro (`application.CardViewSettings`):** enum `Mode
  {TITLE_ONLY, TITLE_PREVIEW, FULL}` (default TITLE_PREVIEW), overrides por
  tarjeta, `effectiveMode = override ?? default`. Serialización JSON manual
  (`{"default":…, "overrides":{…}}`) — Jackson por getters falló
  (FAIL_ON_EMPTY_BEANS con getters privados); lección: para preferencias
  pequeñas, serializar un Map explícito, no el objeto.
- **Persistencia:** `ui.cardview.<boardId>` en app_setting (sin migración);
  `BoardService.cardViewSettingsOf/setCardViewSettings`. Recargado en
  `rebuildAll`.
- **Render:** `MarkdownSummary.render` paramétrico (PREVIEW=3 líneas — era 4;
  FULL=ilimitado; TITLE_ONLY=nada). Tablas/imágenes completas siguen solo
  en la ventana markdown (render nativo de tarjeta).
- **UI global:** botón ☳ en toolbar con menú radio (3 modos) + atajos
  Ctrl+1/2/3; radio sincronizado en cada refresh (`syncCardViewMenu`).
- **UI individual:** clic derecho en tarjeta → menú contextual con los 3
  modos (radio marcado según el efectivo) + "Usar el modo del tablero"
  (solo visible si hay override) que limpia el override de ESA tarjeta.
- i18n `cardview.*` en EN/ES/DE/FR (el test de cobertura los validó —
  pilló el faltante antes que yo). Suite: 126/126 OK. Portable
  regenerado, sello [v2d-20260929-2002], smoke test OK.
### Sesión 2h — 2026-09-29 (Diagnóstico: borrar tablero "no funcionó")
- El usuario intentó borrar un tablero (¿vacío?) y no pasó nada.
- **Reproducción a nivel servicio E integración (SQLite+JSON reales):**
  ambas secuencias (tablero vacío recién creado y activo; tablero con
  columnas/tarjetas/historial) BORRAN CORRECTAMENTE: 130/130 OK con 4
  tests nuevos de repro (`BoardServiceTest` 2, `AppContextDatabase
  ManagementTest` 2 con stack real). Hipótesis del tablero vacío
  descartada.
- **Causa probable del síntoma:** `onDeleteBoard` NO envolvía
  `service.deleteBoard` en try/catch — si algo falla (p. ej. BD bloqueada
  por otra instancia de la app abierta con el mismo data/, que es fácil
  con el portable) la excepción subía sin diálogo y el usuario veía "nada
  pasó". Ahora captura y muestra la causa raíz.
- **A PREGUNTAR/VERIFICAR con el usuario:** ¿tenía otra instancia de la
  app abierta (misma carpeta data/)? ¿vio el diálogo de confirmación? ¿qué
  sello de versión mostraba el título? Pedir el texto exacto si reaparece.
- Portable regenerado: sello [v2d-20260929-2018].

### Sesión 2i — 2026-09-29 (fix flags sin efecto + drop en columna vacía + ancho de columnas)
- **BUG (mío) flags ★/!:** `onToggleCardLabel` no llamaba a `refresh()` —
  el clic SÍ toggleaba en datos, pero la pantalla nunca se redibujaba →
  "no hace nada". Fix: refresh + `undoRedo.sync()` en refresh (el toggle
  agrega entrada de undo y el botón quedó deshabilitado).
- **BUG (diseño original) drop en columna vacía:** el drop-target de
  tarjetas era el VBox de contenido, que tiene TAMAÑO CERO cuando la
  columna está vacía → los drops se rechazaban silenciosamente. Fix:
  el drop-target es ahora el ScrollPane completo del cuerpo de tarjetas
  (`installCardDropTarget(Node)`), que siempre ocupa todo el alto.
- **Ancho de columnas por arrastre (pedido):** asa de 10px ENTRE columnas
  (cursor ↔); arrastrar cambia el ancho de la columna IZQUIERDA en vivo
  (200–800px), al soltar se persiste por tablero en `ui.colwidths.<id>`
  (formato id=ancho;id=ancho). `columnWidthsOf/setColumnWidths` en
  BoardService; estado en BoardController; recargado en rebuildAll. Sin
  refresh durante el drag (el nodo ya quedó en su sitio); aplica también a
  columnas colapsadas NO (se excluyen del restore para no romper la tira).
- **Flags no compresibles:** min width/height USE_PREF_SIZE para que columnas
  angostas no distorsionen ★/!.
- Suite: 130/130 OK. Portable regenerado: sello [v2d-20260929-2111].
- **P2 completa.** Restante del roadmap: P3 (impresión, registro de
  etiquetas) — esperar pedido del usuario.
- **Ejecutables para prueba (mismo día):** `mvn -Pportable package` →
  `target/personal-kanban.jar` (140 MB, fat jar con JavaFX+SQLite natives
  para win/linux/mac; Main-Class=Launcher). Paquete portable armado en
  `target/portable/` (jar + kanban.bat + kanban.sh + LEEME.txt con
  checklist de prueba). Smoke test verificado: arranque con
  `-Dpk.data.dir` crea kanban.db+history+config y el proceso queda vivo;
  también probado el estilo launcher (data/ junto al jar). Nota: el warning
  "Unsupported JavaFX configuration: classes were loaded from 'unnamed
  module'" es normal en fat jars y no afecta. `target/` es gitignore: el
  paquete se regenera con `mvn -Pportable package -DskipTests` + copiar
  launchers.
