# Roadmap, hallazgos y decisiones

*Última actualización: 2026-09-29 · Sesión 2 (Fase P0 implementada + i18n reducido a 4 idiomas)*

Este documento concentra: (1) lo que el usuario pidió, (2) hallazgos
técnicos verificados, (3) decisiones de diseño vigentes con su porqué,
(4) preferencias de trabajo. El estado de ejecución por fase vive en
`PLAN.md` (raíz del repo) — actualizar ambos al avanzar.

---

## 1. Requisitos del usuario (con sus palabras y matices)

1. **Bug de guardado de color** — "intenté cambiar el color de una tarjeta y
   salió 'Could not save board default-board'... en la pantalla de edición
   tiene el color que le quise asignar, solo no se ve reflejado en la vista
   del tablero". → Causa raíz identificada (ver §2.1). **RESUELTO en Sesión 2
   (Fase P0):** `BoardColor.stored()` + rollback en memoria; pendiente de
   prueba manual por el usuario.

2. **Botones del editor markdown mal rotulados** — "no muestran correctamente
   el mensaje... muestran un texto que es el nombre de la propiedad". →
   Claves i18n `dialog.ok`/`dialog.cancel` inexistentes (§2.2). **RESUELTO en
   Sesión 2 (Fase P0):** claves añadidas a los 9 bundles +
   `I18nCoverageTest` que previene regresiones.

3. **Etiquetas con separadores opcionales** — comas u espacios, mezclados,
   con espacios extra: `"et1 et2 et3"`, `"et1,et2,et3"`, `"et1,   et2  et3"`
   ⇒ siempre 3 etiquetas. Aplica al formulario de tarjeta (y al filtro de la
   barra de etiquetas, que usa el mismo parseo). P1.

4. **Etiquetas case-insensitive** — el usuario cree que ya está normalizado
   ("uaz" = "UAZ"); el FILTRO ya es case-insensitive pero el DEDUP de
   `Card.setLabels` NO (guardaría "uaz" y "UAZ" como chips distintos).
   Consolidar en P1: dedup case-insensitive conservando la primera grafía.

5. **Autocompletado de etiquetas** — al escribir en el campo de etiquetas,
   listado de sugerencias por prefijo; selección con ratón o teclado; poder
   seguir tecleando una etiqueta nueva. El usuario confía en el manejo
   estándar de autocompletado. P1. Fuente de sugerencias v1: etiquetas del
   tablero activo; global en P3 con registro de etiquetas.

6. **Chips de etiquetas que resalten** — "un tipo de letra diferente, con un
   fondo de color diferente para cada etiqueta, de manera que resalte cada
   una... tú sabes cómo se maneja en otros programas" (estilo GitHub/Trello).
   P1: fondo redondeado, color determinista por hash, legible en ambos temas.

7. **Tarjetas muy grandes** — tres modos de vista: 1) solo título,
   2) título + primeros 3 renglones, 3) título + texto completo.
   Cambio GLOBAL por tablero ("con un solo botón, comando, instrucción, atajo
   de teclado") y override INDIVIDUAL por tarjeta. El usuario pidió
   "proponer alternativas" de cómo cambiarlo — propuesta: botón en toolbar
   con menú radio + atajos Ctrl+1/2/3 para el tablero; menú contextual en
   cada tarjeta para el override individual. P2.

8. **Impresión de tableros** — explícitamente DESPLAZADA: "dejemos esta
   opción como algo deseable una vez que funcione todo lo demás
   correctamente". P3. No implementar hasta que el usuario lo pida.

9. **Proceso de trabajo** — "haz el plan, documéntalo, nos puede llevar
   varias sesiones, establece prioridades, y yo te diré cuáles iremos
   haciendo, para que yo lo pruebe". → `PLAN.md` en la raíz + checklists de
   prueba manual por fase. El USUARIO elige la fase; no avanzar por cuenta
   propia a otra fase.

## 2. Hallazgos técnicos (✔️ = verificado empíricamente)

### 2.1 ✔️ Causa raíz del "Could not save board default-board"

- `SqliteBoardRepository.bindColumn/bindCard` persisten el color con
  `column.color().name()` / `card.color().name()`.
- `BoardColor` es un record: `name()` es el accessor del componente. Para los
  8 presets vale "blue"…"gray", pero **para un color custom del ColorPicker
  vale `null`** (`BoardColor.fromHex` → `new BoardColor(null, hex)`).
- Columna `color TEXT NOT NULL` ⇒ `setString(i, null)` ⇒ violación NOT NULL
  en el INSERT ⇒ `SQLException` ⇒ `DataAccessException("Could not save
  board …")`.
- Por eso el síntoma es exactamente el reportado: el modelo en memoria se
  muta ANTES de persistir; el fallo deja el dialogo de edición mostrando el
  color elegido (lee del modelo) y la vista del tablero sin refrescar, y la
  BD sin el cambio. **Solo falla con colores custom**, no con la paleta.
- **Fix P0**: `BoardColor.stored()` (hex si custom, name si preset) +
  usarlo en el repositorio; además rollback en memoria si persist falla
  (ver §3.1) y mostrar la causa raíz en el diálogo de error.
- La LECTURA ya soporta ambos formatos (`BoardColor.fromStored`), así que el
  fix es solo de escritura. Retrocompatible con BDs existentes.

### 2.2 ✔️ Causa raíz de los botones "dialog.ok"/"dialog.cancel"

- `CardDetailWindow` (ventana de edición markdown) crea los botones con
  `i18n.text("dialog.ok")` y `i18n.text("dialog.cancel")`.
- Ninguno de los 9 bundles contiene esas claves; `I18n.text` devuelve la
  clave tal cual al no encontrarla → el usuario ve el "nombre de la
  propiedad". Verificado buscando en los 9 properties: cero matches.
- **Fix P0**: añadir las claves a los 9 archivos + test de cobertura que
  compare las claves usadas en el paquete `ui` contra los bundles.

### 2.3 ✔️ Los batches JDBC con 0 filas NO lanzan excepción

Hipótesis descartada experimentalmente con jshell + sqlite-jdbc 3.53.4.0:
`executeBatch()` sobre un statement sin `addBatch` previo devuelve `[]` sin
lanzar; y reutilizar el statement para un segundo batch funciona. La única
causa del bug de guardado es la del §2.1.

### 2.4 Otros hallazgos de lectura de código (no verificados en runtime)

- **`Card.setLabels`**: máx 8 etiquetas, máx 40 chars, lanza
  `IllegalArgumentException` al exceder (llegará al diálogo de error crudo).
  Con la separación por espacios de P1 será más fácil chocar el límite →
  plantear aviso en UI o ampliar límite.
- **Almacenamiento de etiquetas**: unidas con `;` en `card.labels`. Una
  etiqueta que contenga `;` corrompe la lectura. P1 cambia el separador a
  `\u001F` (unit separator) con lectura retrocompatible.
- **`deleteBoard`** en el repositorio concatena el id en SQL string
  (`DELETE FROM board WHERE id = '...'` con escape manual de comillas) —
  inconsistente con el resto parametrizado; higiene menor pendiente.
- **`.card-label-chip`** no tiene regla CSS en light.css ni dark.css → hoy
  las etiquetas se ven como texto plano, sin chip.
- `MarkdownSummary.render(md, 4)` tiene las líneas del resumen fijas en 4
  (P2 parametriza: 3 para modo resumen, ilimitado para modo completo).
- El `Dialogs.parseLabels` del FILTRO y el de la TARJETA comparten método →
  cambiarlo en un solo lugar cubre ambos (P1).
- La app reconstruye TODO el grafo UI al cambiar idioma/tema/tablero/BD —
  decisión consciente y documentada en `BoardController` (grafo pequeño).
  No "arreglar" sin necesidad.

## 3. Decisiones de diseño vigentes (con porqué)

### 3.1 Consistencia UI↔BD al fallar el persist (P0)
Mover `history.push` a después de un `persist()` exitoso; si `persist`
lanza, restaurar el agregado con el memento `before` (rollback en memoria)
y relanzar. Porqué: hoy un fallo de persist deja modelo y BD divergidos y
además registra en el undo un estado que nunca llegó a disco. Alternativa
descartada: persist-first-then-mutate (requeriría separar validación de
mutación en cada comando, más invasivo).

### 3.2 Colores de chips automáticos y deterministas (P1)
Hash del nombre de etiqueta → índice en paleta fija de 8 pares
fondo/texto (definidos en ambos CSS). Porqué: sin tabla de etiquetas no hay
dónde guardar elección de color; determinista = la misma etiqueta siempre
mismo color (memoria visual). La elección manual de color por etiqueta
llega con el registro de etiquetas (P3).

### 3.3 Sugerencias de autocompletado desde el tablero activo (P1)
v1 recolecta las etiquetas existentes en el tablero activo (+ las de la
tarjeta editada). Porqué: no requiere migración; cubre el caso de uso
personal. El listado global entre tableros requiere registro de etiquetas
(P3, migración V5).

### 3.4 Preferencias de vista de tarjeta en SettingsStore, no en esquema (P2)
`ui.cardview.<boardId>` → JSON (default de tablero + overrides por tarjeta)
en la tabla `app_setting`. Porqué: sin migración, sin filas huérfanas al
borrar tableros, y las preferencias de vista no son dominio del tablero.
Tradeoff aceptado: no se exporta en el JSON de tablero (aceptable: es
preferencia local de vista).

### 3.5 Semántica de "modo efectivo" de tarjeta (P2)
`modo efectivo = override individual ?? default del tablero`. El override
individual sobrevive al cambio global (el global solo reescribe el default;
opción "usar el del tablero" en el menú contextual para limpiar el
override). Default inicial de tablero: TITLE_PREVIEW.

### 3.6 Sin migración de esquema en P0/P1/P2 (solo P3 si llega el registro)
Todo lo anterior cabe en código + `app_setting`. Minimiza riesgo y permite
que el usuario pruebe cada fase sin pasos de actualización.

### 3.7 Documentación viva (esta carpeta)
`docs/` + `PLAN.md` se actualizan DENTRO de cada sesión apenas hay
información que no deba perderse, no al final del proyecto. Requisito
explícito del usuario.

### 3.8 Orden de la transacción: persist-first, history-after (P0, Sesión 2)
En `BoardService`: `finishTransaction` persiste y SOLO entonces registra el
undo (`history.push`); si persist falla, restaura el agregado con el memento
before (rollback en memoria). `undo` usa peek→persist→pop: la entrada del
historial no se pierde si el persist falla (undo reintetable). `redo` igual.
Porqué: garantiza que memoria = BD y que el historial nunca registra
estados que no llegaron a disco.

### 3.9 Errores UI con causa raíz (P0, Sesión 2)
`Dialogs.describeFailure(RuntimeException)` recorre la cadena de causas y
anexa la más profunda al mensaje del diálogo (usado en `guarded()` y
`switchDatabase`). Porqué: el mensaje superior ("Could not save board X")
oculta la causa accionable ("NOT NULL constraint failed: card.color").

### 3.18 Drop en columna vacía + ancho de columnas (Sesión 2i)
- **Drop en columna vacía (bug de diseño original):** el drop-target era el
  VBox del contenido, de tamaño CERO sin tarjetas → rechazo silencioso.
  Regla: los drop-targets deben abarcar el área visual completa (hoy el
  ScrollPane del cuerpo de tarjetas), no solo el contenido.
- **Ancho de columnas:** asa de arrastre ENTRE columnas cambia la izquierda
  (200–800px) y persiste por tablero en `ui.colwidths.<boardId>`
  (formato compacto `id=ancho;…` en app_setting). Sin refresh durante el
  drag. Columnas colapsadas excluidas del restore (la tira tiene ancho fijo).
- Lección UI (repetida): toda mutación desde la UI termina en `refresh()`
  o deja la pantalla coherente por otra vía — el flag ★/! sin refresh se
  veía como "botón muerto" aunque los datos cambiaban.

### 3.17 "No funcionó" = buscar error silencioso (lección Sesión 2h)
- Reporte: borrar tablero "no funcionó" (¿tablero vacío?). La lógica era
  correcta (repro servicio + integración con SQLite real: TODO pasa).
  PERO `onDeleteBoard` no capturaba excepciones: cualquier fallo (candidata
  #1: BD bloqueada por otra instancia de la app con el mismo data/ del
  portable) se tragara sin mensaje. Fix: try/catch + describeFailure.
- Regla para futuras sesiones: si el usuario dice "no hizo nada", primero
  buscar rutas de UI que traguen excepciones o diálogos con mensajes
  crípticos; reproducir el flujo exacto en tests de integración con el
  stack real (no solo fakes en memoria).
- Al reportar el usuario el sello de versión del título se evita
  confundir builds viejos con bugs nuevos.

### 3.16 Modos de vista de tarjeta (P2, Sesión 2g)
- **Modo efectivo = override individual ?? default del tablero.** El cambio
  global (toolbar ☳ / Ctrl+1-2-3) solo reescribe el default, así los
  overrides individuales sobreviven al cambio global. "Usar el modo del
  tablero" (menú contextual) limpia el override de esa tarjeta.
- **Persistencia** en `app_setting` clave `ui.cardview.<boardId>` como JSON
  ({"default":…, "overrides":{cardId:mode}}), igual que el colapso de
  columnas: preferencia de vista, no estado de negocio; sin migración.
- **FULL en tarjeta = render nativo comúnmark completo** (títulos, listas,
  énfasis, código); tablas/imágenes ricas solo en la ventana markdown por
  diseño (el frente de tarjeta es nativo, no WebView).
- Lección Jackson: para preferencias pequeñas serializar un Map explícito,
  no el objeto con getters privados (FAIL_ON_EMPTY_BEANS).

### 3.15 Autocompletado + chips (P1, Sesión 2f) — decisiones
- **Sugerencias nunca bloquean:** el popup es una aceleración; escribir una
  etiqueta nueva siempre es posible (el token tecleado se excluye de las
  sugerencias y el campo acepta cualquier texto).
- **Vocabulario:** flags del sistema primero (Importante, Urgente) + todas
  las etiquetas del tablero activo (primera grafía, orden de aparición).
  El global entre tableros llega con el registro de etiquetas (P3).
- **Color de chip determinista por hash** del nombre (case-insensitive)
  sobre 8 pares pastel definidos en ambos CSS: misma etiqueta = mismo
  color en cualquier tarjeta; sin persistir nada. Con el registro de
  etiquetas (P3) se podrá elegir color manual.
- **Almacenamiento \u001F:** separador interno de SQLite; lectura
  retrocompatible con ';' legacy. Nunca usar ';' como separador lógico de
  datos de usuario.

### 3.14 Flags rápidos Urgente/Importante (Sesión 2e)
- Dos etiquetas de SIEMPRE en el sistema: "Urgente" (¡) e "Importante"
  (★), constantes `Card.LABEL_URGENT/LABEL_IMPORTANT`. Son etiquetas
  NORMALES del set (participan en filtro, bulk, futuro color de chips);
  solo la UI les da botón dedicado en cada tarjeta, junto al título.
- Apagado = gris atenuado; encendido = ámbar (★) / rojo (!), en ambos
  temas. `Board.toggleLabel` case-insensitive; añadir respeta el tope de
  8 etiquetas; 1 transacción por toggle (Ctrl+Z lo deshace).
- En modo selección los botones se ocultan (la acción bulk ya cubre
  etiquetas).
- **Lección de esta sesión:** el usuario probó el portable ANTERIOR y no
  vio mejoras (autocompletado/chips no están implementados aún — son P1;
  y no tenía el jar regenerado). Por eso ahora hay SELLO DE COMPILACIÓN
  visible en el título (AppVersion + version.properties generado en build;
  [v2d-<fecha>-<hora>]) y el LEEME lista explícitamente qué está y qué NO.

### 3.13 Carpeta de export/import recordada (Sesión 2d)
- La última carpeta usada (export **e** import comparten memoria) se guarda
  máquina-local en `config.properties` clave `io.lastdir` — junto a db.path
  y recents, no en la BD: es preferencia de la máquina, no del dato.
- **Revalidación en cada lectura**: si la carpeta ya no existe (USB
  retirada), `lastTransferDirectory()` devuelve vacío y el chooser cae a la
  **carpeta personal del usuario** (`user.home`) en TODOS los OS, Windows
  incluido (decisión del usuario: sí, home también en Windows — es real y
  navegable; "Este equipo" es ubicación virtual sin ruta). Nunca se guarda
  una carpeta inexistente.
- `writeConfiguredDatabase` preserva `io.lastdir` al reescribir el config
  (los cambios de BD reescriben el archivo entero).

### 3.12 WebView y <noscript> (hallazgo Sesión 2d)
Con JavaScript deshabilitado, el WebView de JavaFX (WebKit) **muestra el
contenido de `<noscript>`** — lo contrario de la intuición de "solo se ve
con JS off en navegadores con JS activo". La plantilla de Markdown.java
llevaba el aviso "JavaScript is disabled for safety." y se veía en CADA
vista previa. Eliminado: la seguridad la da `setJavaScriptEnabled(false)`,
un cartel visible no añade nada. Regla: nada de `<noscript>` en la
plantilla; si algún día se necesita un fallback, sería contenido real, no
un aviso.

### 3.11 Personalización visual: tinte, colapso y modernización (P1.6, Sesión 2d)
- **Fondo por tinte, no color crudo:** el color elegido (paleta o custom)
  se mezcla sobre la superficie del tema (12% light / 22% dark) para fondo y
  queda como borde exacto si es custom. El color DEFAULT no tinta. Porqué:
  mantener legibilidad y calma; el color crudo satura y el texto pierde
  contraste. Si el usuario pide algún día "color sólido", será una opción
  de intensidad, no el default.
- **Colapsar columnas:** preferencia UI por TABLERO en `app_setting`
  (`ui.columnstate.<boardId>`, ids con `;`), NO en el dominio: no es estado
  del negocio ni del undo; al borrar tablero la fila de settings queda
  huérfana pero inofensiva. La tira colapsada muestra título vertical,
  contador y expandir; sigue siendo drag-source para reordenar.
- **Modernización de tarjetas solo en CSS** (radio 10, sombra, hover):
  cero cambios de código; tema light y dark.

### 3.10 Selección múltiple y acciones bulk (P1.5, Sesión 2b)
- Modo selección por columna (botón ☐ en el header; clic simple alterna la
  selección de tarjetas; ✕ en la barra sale del modo). Barra propia con
  contador y acciones: etiquetas añadir/quitar, color, mover a columna,
  eliminar selección (con confirmación).
- **Movimiento bulk atómico:** `Board.moveCardsToColumn` valida (ids
  existentes + WIP del destino para todo el lote) ANTES de mutar. Si no cabe,
  `WipLimitBulkException.excess()` dice cuántas tarjetas sobran y la UI lo
  traduce ("deselecciona al menos N"). Nada se mueve de forma parcial.
- **Orden preservado:** las tarjetas llegan al final del destino en el orden
  de selección del usuario; las ya presentes en el destino no se tocan.
- **Etiquetas bulk:** añadir es case-insensitive y conserva la primera
  grafía ("uaz"+"UAZ" → un chip); quitar igual de tolerante.
- **Undo:** toda acción bulk = 1 transacción = 1 entrada de undo (el comando
  es un objeto y el memento captura antes/después).
- `WipLimitExceededException` expone `limit()` para mensajes más claros.

## 4. Ideas de brainstorm en reserva (no comprometidas)

- **Escritura incremental en SQLite**: `replaceAll` (borrar y reinsertar todo
  el tablero por save) escala bien para uso personal (~cientos de tarjetas);
  si algún día se nota lento, cambiar a upserts por tarjeta + snapshots solo
  para undo. NO hacer ahora; documentado como dirección futura.
- **Registro de etiquetas** (tabla `label`): colores elegidos por el usuario,
  renombrado propagado, autocompletado global. P3.
- **Clic en chip = filtrar** por esa etiqueta (barato y útil, tras P1).
- **Impresión vía export HTML** (alternativa a PrinterJob sobre nodo JavaFX):
  reutiliza commonmark + estilos, pagina mejor, y sirve de backup visual.
  Evaluar cuando el usuario pida P3-impresión.
- **Limpieza menor**: parametrizar `deleteBoard`, import duplicado en
  `ColumnViewBuilder`, aviso por límite de etiquetas (ver §2.4).

## 5. Preferencias y contexto del usuario

- **i18n reducido (Sesión 2):** solo EN, ES, DE, FR. Los bundles ru/hi/zh/ja
  fueron eliminados y `I18n.SUPPORTED` acortado. Porqué: menos superficies de
  traducción que mantener. Un setting viejo `ui.language=ru|hi|zh|ja` cae al
  bundle base (inglés) sin errores. Todo texto NUEVO solo necesita los 4
  bundles (lo enforcea `I18nCoverageTest`).
- **Acciones destructivas lejos del alcance del clic (Sesión 2b):** el botón
  "eliminar todas las tarjetas" se quitó del header de columna (fácil de
  equivocarse, palabras del usuario) y vive en el menú ⋯ de la columna. En su
  lugar: botón ☐ de selección múltiple con acciones bulk (etiquetas ±, color,
  mover, borrar selección). Ver §3.10.
- **El nombre del tablero siempre visible (Sesión 2c):** el usuario creó un
  tablero "Doctorado" y no encontraba el nombre en la pantalla. Ahora la
  toolbar muestra un badge con el tablero activo y el título de la ventana
  lo incluye. Regla: la identidad del tablero activo debe verse sin abrir
  ningún menú.
- **Creación de tableros con columnas (Sesión 2c):** tras el nombre, un
  ChoiceDialog de plantilla con **"Plantilla Kanban Estándar"
  preseleccionada** (Por hacer/Haciendo/Hecho, opción más común — palabras
  del usuario), **Personalizadas** (número "4" ⇒ "Columna 1..N" localizado, o
  nombres por comas) y **Sin columnas**. Split SOLO por comas a propósito (los
  títulos de columna sí pueden contener espacios — a diferencia de las
  etiquetas, que aceptan también espacios como separador). Límite 1..12;
  entrada inválida NO crea el tablero (se reintenta desde el nombre).
  Cancelar en cualquier paso no crea nada.
- Comunica en **español**; respuestas y documentación en español. Código,
  identificadores y comentarios de código en inglés (convención del repo).
- Quiere **probar él mismo** cada fase antes de continuar (checklists de
  prueba manual). Sesiones cortas, guiadas por `PLAN.md`.
- Valoriza la trazabilidad de decisiones ("propón alternativas", "establece
  prioridades", "documéntalo"). Espera que el agente use criterio propio
  sobre UX estándar ("tú sabes cómo se maneja el autocompletado").
- Usa la app con datos reales (tablero "uaz" = Universidad Autónoma de
  Zacatecas, probablemente) — los fixes no deben romper BDs existentes.
- Entorno del usuario: Windows (rutas y jshell en español, launchers .bat).

## 6. Convenciones para las sesiones de trabajo

1. Leer `docs/README.md` + `PLAN.md` al inicio; actualizar
   `roadmap-and-decisions.md` en cuanto aparezca una decisión/hallazgo.
2. Cada fase: implementar + tests + actualizar PLAN.md (estado y checklist)
   + compilar `mvn test` si hay dependencias en el repo local de Maven
   (⚠️ JavaFX no está en el repo local offline — ver project-overview.md).
3. No avanzar a otra fase sin indicación del usuario.
4. Commits solo si el usuario lo pide (estilo del repo: mensajes
   descriptivos en inglés).
