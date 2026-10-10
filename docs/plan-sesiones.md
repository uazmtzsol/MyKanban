> Estado: **Sesión 11 en curso** (pedido del usuario el 2026-10-09). Sesión 10
> completada y documentada abajo. Este documento es la cinta de progreso:
> qué se hará, en qué orden, con qué criterio, y sirve para reanudar si la
> sesión se interrumpe.

# Plan por sesiones — mejoras adicionales de Personal Kanban

## Sesión 11 — seis correcciones/mejoras (pedido del usuario, prioridad actual)

> Pedidas el 2026-10-09. Cada punto se desarrolla, se prueba, se documenta su
> estado y se hace commit + push **antes** de pasar al siguiente.

### S11-1 — Quitar la imagen de fondo (toda la funcionalidad)

El usuario no la va a usar: eliminar completamente.

- `ui/BackgroundFiles` (+ test `BackgroundFilesTest`).
- En `BoardController`: `backgroundSpec`, `applyBoardBackground`,
  `paintBoardRoot`, `paintPlainBoardRoot`, `dimmedImageUri`, la migración de
  arranque (`repairSpec`) y el manejo en `onShowPreferences`. Sin la imagen
  inline, el color del `.root` vuelve a gobernar el CSS del tema (como antes
  de la sesión 4).
- `PreferencesDialog`: pestaña «Fondo» completa + `BackgroundChoice` +
  firma `show(seed, spec)` → `show()` sin parámetros ni retorno.
- `BoardService`: `background()/setBackground()` y
  `boardBackgroundOf/setBoardBackground` (+ claves `ui.background*`).
- i18n: `prefs.background.*`, `prefs.tab.background` (5 bundles).
- Tests: `BackgroundFilesTest`, `BoardBackgroundCssTest`,
  `SessionFourServiceTest.backgroundPreferenceRoundTripsAndClears`.
- **NO tocar** el color de fondo de columna (`column.background`, otra
  característica).

### S11-2 — Esquema de colores: arreglo del modo obscuro + personalización

Motivo del usuario: en modo obscuro «el texto es gris sobre un fondo muy
obscuro». Arreglar contraste **y** hacer el esquema editable.

1. **Contraste del tema obscuro** (dark.css): aclarar los grises de texto
   (#9ca3af → #c3cbd6/#b6bec9, banderas apagadas #4c5568 → #6b768c).
2. **Esquema personalizable** con valores por defecto:
   - Nuevo modelo puro `application/ThemeColors`: slots `bg`, `column`,
     `card`, `text`, `selected` × dos temas; almacenamiento
     `ui.scheme.<slot>.<theme>`; vacío = default.
   - `BoardService`: `schemeColor`, `setSchemeColor`, `resetScheme`.
   - UI: sección «Esquema de colores» en Preferencias → Apariencia, con
     pickers por slot y tema, **vista previa en vivo** (mini-tablero con una
     tarjeta «seleccionada») y botón **Restaurar valores por defecto**.
   - El color `selected` personaliza el resaltado de la **tarjeta actualmente
     seleccionada** (confirmado con el usuario: es la tarjeta, que con S11-5
     se marca al hacer clic).
   - Aplicación: CSS generado (`ui/ThemeOverride`, puro y testeable)
     sobreañadido a la hoja del tema en la escena principal y en las
     ventanas secundarias.
3. Tests puros de `ThemeColors`/`ThemeOverride` + comprobación de cableado.

### S11-3 — Bug: markdown en una nota crea varias notas

Causa raíz: `CardNotes` usa `## ` como separador de notas; un `## ` del
cuerpo (markdown) crea una nota nueva. Decisión de diseño: **marcador nuevo
que el markdown ignora** — `<!--pk-note: Título-->` — con parseo
retrocompatible: si el texto no tiene marcadores nuevos, se usa el split
legado por `## `; `serialize()` siempre escribe el formato nuevo. Los cuerpos
con subtítulos `## ` sobreviven intactos. Tests en `CardNotesTest`.

### S11-4 — Gestión de etiquetas (alcance: TODOS los tableros, confirmado)

- Listar etiquetas en uso (todos los tableros) + predefinidas
  (Urgente/Importante/Archivada) marcadas como no editables/no borrables.
- **Renombrar**: propagado a todas las tarjetas de todos los tableros
  (identidad sin distinguir mayúsculas: «et1» = «ET1»); validaciones: nombre
  válido, sin colisión (case-insensitive) con existente ni con predefinida;
  migra la regla de colores de la etiqueta si existe.
- **Borrar**: quita la etiqueta de todas las tarjetas de todos los tableros,
  con confirmación; borra su regla de colores.
- Dominio: `Board.renameLabel/removeLabelEverywhere` + comandos
  `RenameLabelCommand`/`RemoveLabelCommand`; servicio itera los tableros del
  catálogo (el activo vía `execute()` = deshacerable; el resto cargando,
  mutando y guardando cada tablero — el deshacer no cubre los no activos,
  decisión aceptada por el usuario).
- Diálogo «Gestionar etiquetas…» desde el menú Tablero.
- **Atajo sugerido e implementado: `Alt+E`** (E de etiquetas; libre respecto a
  Alt+I/Alt+U/Alt+←/→): abre un menú emergente sobre la tarjeta con foco con
  todas las etiquetas del vocabulario en casillas de verificación
  (añadir/quitar sin entrar a la edición). Fijo como Alt+I/Alt+U.

### S11-5 — Clic en una tarjeta = tarjeta «actualmente seleccionada»

Un clic simple (fuera del modo selección múltiple) marca la tarjeta como
seleccionada/enfocada (`cardFocused` + `requestFocus` → resaltado
`.card:focused`, con el color de S11-2). Así el teclado (Enter, Delete,
Alt+I/U/E) actúa sobre la tarjeta en la que se hizo clic.

### S11-6 — Excepción al abrir «Ayuda → Personalizar atajos de teclado»

Causa raíz encontrada: `KeyboardShortcutsDialog` construye las filas con el
nombre i18n de la acción, y la celda de la lista llama
`GlobalShortcuts.Action.fromStorageNameOrThrow(...)`, que solo reconoce los
nombres de almacenamiento («next», «prev»…) y **revienta** con el nombre de
enum («NEXT_CARD») o la clave i18n al pintar. Arreglo en dos frentes:
`fromStorageNameOrThrow/isKnownActionName` aceptan nombre de enum O de
almacenamiento (tolerante, con tests), y el diálogo se reescribe para llevar
el enum directamente en la fila sin re-parsear nombres.

### Criterios comunes de la sesión

- i18n completa (5 bundles) para textos nuevos; ArchitectureTest e
  I18nCoverageTest en verde; commit + push por punto; documentar estado y
  hallazgos tras cada uno.

## Sesión 10 — cuatro características nuevas (pedido del usuario)

> Pedidas el 2026-10-09. Completadas: navegación con teclado, resumen de 3
> renglones, exportar tarjeta, archivar/borrar. Detalle abajo.

### F1 — Navegación con teclado completa (y fuera los botones de la toolbar)

Motivo del usuario: con los **botones** de la barra de herramientas la
navegación funciona (la tarjeta se resalta y se edita), pero esos botones no
se quieren; se quiere **teclado**, y hoy el teclado no da esa funcionalidad.

Teclas a implementar:

| Tecla | Acción |
|---|---|
| `↓` | Siguiente tarjeta de la columna actual (si estaba en la última, la primera) |
| `↑` | Tarjeta anterior (si estaba en la primera, la última) |
| `Alt+→` | Primera tarjeta de la siguiente columna (si estaba en la última, la primera) |
| `Alt+←` | Primera tarjeta de la columna anterior (si estaba en la primera, la última) |
| `Ctrl+→` | Primera tarjeta de la primera columna del **siguiente tablero** |
| `Ctrl+←` | Primera tarjeta de la primera columna del tablero anterior |
| `1`…`9` | Selecciona la tarjeta en esa posición de la columna actual |
| `Enter` | Edita la tarjeta seleccionada |
| `Alt+I` | Alterna la etiqueta «importante» en la tarjeta seleccionada |
| `Alt+U` | Alterna la etiqueta «urgente» en la tarjeta seleccionada |
| `Delete` | Borra la tarjeta seleccionada **con confirmación** |

**Hecho (commit de la sesión 10):**

- Los tres botones de navegación de la toolbar **eliminados**; el mapa
  completo vive en `BoardController.onBoardKeyPressed`.
- Cambio clave de diseño: se usa un **`scene.addEventFilter(KEY_PRESSED)`**,
  no aceleradores. Un acelerador de JavaFX llega *después* de que el control
  con foco trate el evento, así que las flechas "no hacían nada" cuando el
  foco estaba en un control que se los come; el filtro se ejecuta **antes**
  y garantiza la navegación con cualquier foco.
- Lógica de navegación extraída a **`ui/CardNavigator`** (Java puro, sin
  JavaFX) → testeable: dentro de columna con vuelta al inicio/fin, salto de
  columna con vuelta al tablero **ignorando columnas vacías** (la tecla nunca
  queda muda), dígito = posición en la columna actual, sin foco = empieza
  arriba/abajo del tablero.
- `Enter` edita, `Escape` suelta el foco y `Alt+I`/`Alt+U`/`Delete` actúan
  sobre la tarjeta enfocada (`Delete` = el mismo diálogo de confirmación de
  siempre; al borrar, el foco queda en la tarjeta que ocupa su lugar).
- `Ctrl+←/→` cambia de tablero y enfoca la primera tarjeta de la primera
  columna del tablero destino.
- El foco **no se secuestra** mientras se escribe en un campo ni dentro de
  listas/combos (ahí las flechas siguen siendo del control); `Escape` siempre
  suelta el foco de la tarjeta. La **vista de procesos conserva sus teclas
  propias** (flechas/Delete del diagrama) y aquí solo se le añaden `Escape`
  y `Enter`.
- **Bug encontrado y corregido de paso:** los atajos guardados (`ui.shortcuts`)
  se escribían con «Ayuda → Atajos de teclado» pero **nunca se volvían a leer**
  (`service.globalShortcuts()` no tenía ningún llamante): al reiniciar, todo
  volvía a los valores por defecto. Ahora se cargan en el constructor y en
  `rebind()` (cambio de base de datos).
- Tests: `CardNavigatorTest` (13) + `KeyboardNavigationTest` (7, a nivel
  fuente, mismo estilo que `CardKeyboardFocusTest`). Suite **325/325 OK**
  (1 skip: el endpoint PHP sin servidor).
- **Pendiente de F1:** prueba manual con el teclado real (la suite no puede
  arrancar el toolkit JavaFX) y, si se quiere, añadir las teclas fijas a la
  ventana de ayuda F1 (hoy solo lista las configurables).

Criterios de hecho:

- Quitar los tres botones de navegación de la toolbar (`←`, `→`, `↵`). ✅
- El manejo pasa a un **filtro de eventos de la escena** (no solo
  aceleradores): así las teclas funcionan **sin importar qué control tenga
  el foco**, salvo cuando se está escribiendo en un campo de texto (ahí no
  se secuestran las teclas).
- `←`/`→` siguen existiendo como atajo configurable (siguiente/anterior
  tarjeta visible del tablero completo), coexistiendo con las teclas nuevas.
- No romper el diálogo de atajos (Ayuda → Atajos de teclado) ni el guardado
  de atajos (`ui.shortcuts`).
- Tests: lógica de navegación testeable sin JavaFX + comprobaciones de
  cableado (source-level, como `CardKeyboardFocusTest`).
- i18n en los 4 idiomas para cualquier texto nuevo.

### F2 — Las tarjetas en modo «resumen (3 renglones)» y «extensa» se ven iguales

Causa raíz encontrada durante la planificación: `MarkdownSummary.render(md,
maxLines)` cuenta **bloques** del AST, no líneas visibles. Una descripción
de un solo párrafo con 10 saltos de línea es *un* bloque → el modo
`TITLE_PREVIEW` (3 líneas) la pinta entera, idéntica a `FULL`.

Corrección: contar líneas visibles (saltos `\n` incluidos), cortar en 3 y
marcar el corte con `…` en el modo resumen. `FULL` sigue pintando todo.
Tests unitarios nuevos sobre `MarkdownSummary`.

**Hecho:**

- Nuevo `ui/markdown/LineBudget` (Java puro): presupuesto de **líneas y
  caracteres**, corta en frontera de palabra y marca el corte con `…` (el
  `\n` final se reemplaza para que el `…` no abra una cuarta fila).
- `MarkdownSummary.render(md, maxLines, maxChars)` ahora separa **bloques y
  ítems de lista con un `\n` explícito**. Hallazgo importante: `TextFlow`
  coloca sus hijos *en línea*, así que sin ese `\n` dos párrafos (o dos
  viñetas) se dibujaban pegados en UNA fila — por eso el recuento por bloques
  no solo estaba mal, sino que las filas reales solo existían donde el
  usuario escribía saltos.
- `CardViewBuilder`: preview = **3 filas y ≈120 caracteres** (el área mide
  220px a 12px ≈ 36 caracteres/fila); `FULL` = sin límites.
- Tests: `LineBudgetTest` (8) + `MarkdownSummaryTest` (9, comprueba que
  preview y extensa ya **no** coinciden en una descripción larga). Suite
  **342/342 OK** (1 skip PHP).
- **Pendiente F2:** verificación visual manual en claro/oscuro y con
  descripciones que sí tienen viñetas/markdown (la suite no pinta la UI).

### F3 — Exportar una tarjeta (txt, markdown, pdf)

Exporta una tarjeta con las secciones marcadas por el usuario:

1. **Datos**: título, descripción, fecha límite, etiquetas.
2. **Lista de tareas** (checklist con estado `[x]`/`[ ]`).
3. **Notas** — con selección de *qué* notas exportar (por defecto todas).
   Las notas son un texto plano; se permite elegir rango/líneas.
4. **Registros de tiempo**: `inicio – fin : nota`.

Formatos: **txt**, **markdown** y **pdf** (reutilizando PDFBox, como el
exportador de tablero). Diálogo de opciones (secciones + formato), guardado
con `FileChooser` (carpeta recordada `io.lastdir`). Accesible desde el menú
de la tarjeta y/o el diálogo de detalle.

Criterios de hecho: generación de txt/md testeable sin JavaFX (clase
pura en `application` o `ui` sin dependencias de escena), i18n completa,
ArchitectureTest en verde.

**Hecho:**

- **`ui/CardExporter`** (puro, sin JavaFX): renderiza la tarjeta a txt o
  markdown con las secciones marcadas y las notas seleccionadas (todas por
  defecto, en el orden de `CardNotes`). Marcadores `- [x]`/`- [ ]` y
  registros `inicio – fin : nota` (sin fin = «en curso»).
- **`ui/pdf/CardPdfWriter`**: PDF de **texto seleccionable** paginado en A4
  (PDFBox puro, sin captura de pantalla). Helvetica solo admite WinAnsi →
  los emoji/Caracteres fuera de tabla se sustituyen por `?` en vez de
  reventar la exportación; líneas largas con corte por palabras.
- **Diálogo** (`Dialogs.exportCardDialog`): casillas de sección, lista de
  notas con casillas (todo seleccionado) y formato (txt/md/pdf). Aceptar sin
  ninguna sección actúa como Cancelar.
- **Acceso:** menú contextual de la tarjeta → **Exportar tarjeta…** →
  FileChooser con nombre derivado del título (carpeta recordada `io.lastdir`).
- i18n: 16 claves nuevas en los 5 bundles.
- Tests: `CardExporterTest` (8) + `CardPdfWriterTest` (6, reabre el PDF y
  extrae el texto). Suite **356/356 OK** (1 skip PHP); ArchitectureTest e
  I18nCoverageTest en verde.
- **Hallazgo (bug existente corregido):** el bucle de visibilidad del menú
  contextual ocultaba **todos** los ítems «plain» (incluido «Mover/copiar a
  otro tablero» y los separadores) hasta que la tarjeta tenía un override
  de vista; ahora solo se oculta el ítem de «restablecer vista».
- **Pendiente F3:** prueba manual (guardado real de los 3 formatos y
  apariencia del PDF) — la suite valida el contenido y el PDF, no el diálogo.

### F4 — Limpieza de tableros: archivar y borrar tarjetas

- **Archivar**: revisar la viabilidad de ocultar tarjetas que ya no interesan
  (p. ej. terminadas) sin borrarlas. Decisión de diseño documentada aquí
  antes de implementar (posible etiqueta `#archivado` + filtro, o campo
  nuevo; sin migración de esquema si se puede evitar).
- **Borrado real**: borrar tarjetas que ya no sirven, **incluidos sus
  archivos adjuntos** (carpeta de adjuntos del programa) y sus notas —
  comprobar que `AttachmentStore` limpia los archivos al borrar la tarjeta
  y arreglarlo si no lo hace.
- Herramienta de mantenimiento: revisar/borrar adjuntos huérfanos
  (archivos en la carpeta de adjuntos sin tarjeta que los referencie).

**Decisión de diseño (archivar):** implementado como **etiqueta reservada
`Archivada`** (`Card.LABEL_ARCHIVED`) + casilla «Mostrar tarjetas
archivadas» en la barra de filtro. Sin migración de esquema: archivar es
un toggle de etiqueta (deshacible, sincronizable, exportable) y lo que oculta
es la vista. El gate vive en un solo sitio —
`BoardController.matchesProcessFilter` — por el que pasan tanto el
renderizado como el navegador por teclado, así que las flechas y los
dígitos no aterrizan en una tarjeta oculta.

**Hecho (F4):**

- Menú contextual de tarjeta: **Archivar / desarchivar** (alterna la
  etiqueta; Ctrl+Z lo deshace).
- Barra de filtro: botón 🗄 «Mostrar tarjetas archivadas» (por defecto
  apagado = las archivadas están ocultas). Visible solo en sesión (es una
  decisión de vista, no de datos).
- **Borrado que arrastra los archivos:** cada ruta de borrado (tarjeta,
  selección múltiple, vaciar columna, borrar columna, borrar tablero)
  mueve `attachments/<cardId>` a `attachments-trash/<cardId>` ANTES de
  borrar la fila. Las notas y el checklist van con la propia fila (ya
  estaban en la BD y se borran con ella).
- **Deshacer seguro:** `refresh()` restaura la carpeta de la papelera si la
  tarjeta volvió a existir — borrar y Ctrl+Z no pierde ni un archivo.
  (Un borrado directo habría roto esto: los archivos viven fuera de la BD.)
- **Archivo → Limpiar archivos adjuntos huérfanos…**: lista las carpetas
  (vivas y de papelera) cuya tarjeta **no existe en ningún tablero** de la
  base, pide confirmación con el recuento y las borra. Si un tablero no
  se puede leer, no se borra nada de él (nunca se arriesgan archivos).
- Tests: `AttachmentTrashTest` (6, papelera/restauración/huérfanos) +
  `BoardCleanupTest` (5, cableado a nivel fuente). Suite **367/367 OK**
  (1 skip PHP); i18n y ArchUnit en verde (6 claves nuevas × 5 bundles).
- **Límite conocido:** en la **vista de procesos** las tarjetas archivadas
  siguen apareciendo (el grafo de disposición se calcula con todas las
  tarjetas); filtrarlas ahí toca el algoritmo de disposición y se deja
  documentado en vez de meterlo a última hora.
- **Pendiente F4:** prueba manual (archivar/desarchivar, borrar con
  adjuntos + Ctrl+Z, y el diálogo de limpieza).

## Fases originales (en espera hasta que el usuario diga lo contrario)

## Qué vamos a hacer

Vamos a implementar las mejoras adicionales recomendadas en
[`docs/adiciones-propuestas.md`](adiciones-propuestas.md). No las hacemos todas
juntas. Las organizamos en fases pequeñas, cada una con un propósito claro, sus
propios tests y sus propias claves i18n. Al terminar cada fase, se hace commit y
push del avance, y se espera tu revisión antes de pasar a la siguiente.

## Criterio general

1. Respetar la arquitectura hexagonal del proyecto (ArchUnit lo vigila).
2. Toda cadena visible nueva va a los 4 bundles (EN/ES/DE/FR) y pasa la prueba
   de cobertura de i18n.
3. Cada fase tiene tests (lo más posible en dominio/application; la UI se prueba
   también manualmente).
4. No mezclar fases en un mismo commit; una fase, un avance versionado.
5. Si durante una fase encontramos una mejora no planeada y de bajo riesgo, la
   documentamos al final y pedimos decisión; si es media/alta complejidad, la
   dejamos como pendiente documentada y no la mezclamos.
6. No añadimos migraciones de esquema ni dependencias nuevas por defecto; esto
   sigue las reglas de proyecto documentadas.

## Prioridades elegidas

El orden está pensado para avanzar con lo más barato y útil primero, reusando lo
que ya existe:

1. **Fase A — Navegación por teclado básica** (alta utilidad diaria, bajo
   riesgo, usa comandos y filtros ya existentes).
2. **Fase B — Búsqueda en el tablero activo** (resuelve el caso “muchas tarjetas
   y no encuentro”).
3. **Fase C — Visor de copias de conflicto** (cierra el sync que ya está
   implementado).
4. **Fase D — Backup simple** (calma operativa para datos reales).

## Por qué este orden

- La navegación por teclado y la búsqueda entran con el menor acoplamiento
  posible: no tocan persistencia, ni modelo de tablero, ni sync, y dan retorno
  rápido en el día a día.
- El visor de copias de conflicto cierra un asunto ya importante (el sync ya
  resuelve conflictos pero no los muestra), pero requiere una pantalla propia y
  un puerto/lectura de copias; por eso va después, cuando ya tenemos menos
  riesgo y ya vimos cómo trabaja el proyecto.
- El backup es una mejora de tranquilidad, no de flujo diario; por eso va al
  final de este plano, encaja bien como sesión más operativa.

## Fases

### Fase A — Navegación por teclado básica

Objetivo: que sea posible movirse por las tarjetas del tablero activo con
flechas (una dirección a la vez, comportamiento razonable si el tablero está
muy poblado), editar la tarjeta con foco y salir del modo de edición, sin que
la app se olvide de dónde está el foco.

Qué cubre:
- mover entre tarjetas del tablero activo con flechas (una dirección a la vez,
  comportamiento razonable si el tablero está muy poblado),
- Enter para editar la tarjeta con foco,
- Esc para salir sin perder el contexto cuando proceda,
- mantener un foco estable entre reconstrucciones de UI (idioma, cambio de
  tablero, etc.) cuando proceda.

Qué NO cubre esta fase:
- no inventamos un modo de navegación global complejo,
- no tocamos la persistencia ni el modelo de tablero,
- no asumimos modos de vista avanzados ni process view; si el foco ya existe en
  process view, lo respetamos y lo dejamos compatible.

Criterios de hecho:
- la app compila y los tests nuevos pasan,
- las claves de texto necesarias están en los 4 bundles,
- no hay dependencias nuevas ni migraciones,
- hay checklist de prueba manual actualizada.

**Lo hecho en esta sesión (Fase A):**

- Corregido un `}` extra en `BoardController.java` que rompía la estructura de la clase.
- Integrado el diálogo de edición de atajos (`KeyboardShortcutsDialog`) en **Ayuda → Atajos de teclado…**.
- Añadidas las claves de i18n faltantes del diálogo de atajos y del diálogo de captura en los 4 bundles (EN/ES/DE/FR) y en `messages_en.properties`.
- Verificado, sin ejecutar compilación: balance de llaves de `BoardController.java` (372/372), y que ya no faltan claves de UI en ningún bundle (escaneo completo de `i18n.text(...)` en `ui/`).

**Lo pendiente para que la Fase A esté “hecha y verificable”:**

- Compilar en tu entorno (`mvn compile`) y ejecutar la suite (`mvn test`), sobre todo `I18nCoverageTest` y los tests nuevos (`GlobalShortcutsTest`, `TextCaptureDialogTest`).
- Probar manualmente: flechas entre tarjetas, Enter edita la focalizada, Esc cancela foco/diálogo, y que el diálogo de atajos se abre desde Ayuda y permite reasignar y guardar.
- Chequear el comportamiento real de `TextCaptureDialog` al probarlo (el test actual no demuestra la captura real; ver nota en `src/test/java/com/personalkanban/ui/TextCaptureDialogTest.java`).

### Fase B — Búsqueda en el tablero activo

Objetivo: poder buscar tarjetas dentro del tablero activo por texto que aparece
en el título y, cuando proceda, en campos que ya existen, con feedback claro de
qué se encontró.

Qué cubre:
- búsqueda en tablero activo por título al menos,
- feedback visible de resultados y de “no hay resultados”,
- acceso rápido desde teclado o barra de filtro si conviene,
- integración con filtros ya existentes sin romperlos.

Qué NO cubre esta fase:
- búsqueda cross-board/ global (esa es un paso posterior si se quiere),
- no construimos un motor de búsqueda propio si no hace falta.

Criterios de hecho:
- la app compila y los tests nuevos pasan,
- i18n completa,
- hay checklist de prueba manual actualizada.

### Fase C — Visor de copias de conflicto

Objetivo: que el usuario pueda ver las copias de conflicto que el sistema ya
escribe en disco, entender qué fields entraron en conflicto y, si quiere,
restablecer algo desde esa copia.

Qué cubre:
- lista de copias existentes de un tablero,
- lectura de una copia y visualización del conflicto,
- acción de restauración o referencia si procede,
- cierre del ciclo §4.1 del diseño de sync.

Qué NO cubre esta fase:
- no reescribe el motor de sync; usa lo que ya existe,
- no supone un flujo multi-dispositivo completo; es el visor de lo que ya se
  guarda.

Criterios de hecho:
- la app compila y los tests nuevos pasan,
- i18n completa,
- hay checklist de prueba manual actualizada.

### Fase D — Backup simple

Objetivo: una rotación básica de copias del archivo de base de datos y la
capacidad de revisar el estado de la base antes de moverla o confiar en ella.

Qué cubre:
- backup con timestamp y política de N copias,
- verificación básica de estado cuando corresponda,
- preferencia de activar/desactivar en el menú de preferencias si conviene.

Qué NO cubre esta fase:
- no convierte la app en algo distribuido ni de audit log avanzado,
- no añadimos dependencias si no hace falta.

Criterios de hecho:
- la app compila y los tests nuevos pasan,
- i18n completa,
- hay checklist de prueba manual actualizada.

## Checklist general del proyecto (para no perder el hilo)

- [ ] El proyecto compila offline sin cambios nuevos.
- [ ] La suite de tests está verde antes de empezar una fase.
- [ ] Cada fase añade tests y no elimina comportamiento existente.
- [ ] Cada fase tiene sus claves i18n en los 4 bundles.
- [ ] Cada fase pasa ArchitectureTest e I18nCoverageTest.
- [ ] Cada fase termina con commit + push antes de pasar a la siguiente.
- [ ] Cada fase termina con checklist de prueba manual actualizado.
- [ ] Las mejoras no planeadas se documentan al final de cada fase.

## Estado de cada fase

| Fase | Planificada | En desarrollo | Hecha | Pendiente para revisión | Notas |
|---|---|---|---|---|---|
| **S11-1 — Quitar imagen de fondo** | sí | sí | **sí** | prueba manual | `BackgroundFiles` + tests fuera; pestaña Fondo eliminada; `ui.background*` fuera; `PreferencesDialog.show()` sin args |
| S11-2 — Esquema de colores | sí | no | no | sí | contraste obscuro + `ThemeColors`/`ThemeOverride` + vista previa + reset |
| S11-3 — Notas y markdown | sí | no | no | sí | marcador `<!--pk-note:-->` retrocompatible con `## ` legado |
| S11-4 — Gestión de etiquetas | sí | no | no | sí | todos los tableros, atajo `Alt+E`, predefinidas protegidas |
| S11-5 — Clic = seleccionada | sí | no | no | sí | clic simple marca `cardFocused` |
| S11-6 — Excepción atajos | sí | no | no | sí | `fromStorageNameOrThrow` tolerante + diálogo reescrito |
| **S10-F1 — Navegación con teclado** | sí | sí | **sí** (`c0ed25f`) | prueba manual | filtros de escena, `CardNavigator`, botones fuera; +20 tests |
| **S10-F2 — Resumen vs. extensa** | sí | sí | **sí** (`5193d63`) | prueba manual | `LineBudget` (líneas+caracteres) y `\n` entre bloques; +17 tests |
| **S10-F3 — Exportar tarjeta** | sí | sí | **sí** (`ac5c62a`) | prueba manual | `CardExporter` + `CardPdfWriter`; diálogo con secciones/notas/formato; +14 tests |
| **S10-F4 — Archivar/borrar tarjetas** | sí | sí | **sí** (`5dc2882`) | prueba manual | etiqueta `Archivada` + papelera de adjuntos + limpieza; +11 tests |
| Fase A — Navegación por teclado | sí | sí | **sí** (commit `6225456` + `e948e44`, suite verde) | no | completada y verificada en la sesión 9/10 |
| Fase B — Búsqueda en tablero activo | sí | no | no | sí | depende de que A esté revisada y aprobada |
| Fase C — Visor de copias de conflicto | sí | no | no | sí | después de B |
| Fase D — Backup simple | sí | no | no | sí | después de C |

## Mejoras no planeadas (detectar durante el trabajo)

Si durante una fase encontramos una mejora que no estaba en este plan, se anota
aquí, se describe brevemente el porqué y se deja la decisión para el final de la
fase o para la próxima sesión. Ejemplos del tipo de cosas que podemos encontrar:
- un atajo o comportamiento colateral que conviene también al resto del tablero,
- una clave i18n que conviene añadir aunque el alcance sea pequeño,
- una opción de UX que surge al probar la fase y que no estaba en las
  recomendaciones iniciales.

### Hallazgos de esta sesión (Fase A)

- `BoardController.java` tenía un `}` extra después de `GlobalShortcuts globalShortcuts()`, lo que hacía que el archivo no compilase. Corregido (balance de llaves: 372/372).
- `KeyboardShortcutsDialog` ya estaba en disco, pero no tenía entrada de menú que lo abriera. Ahora se abre desde **Ayuda → Atajos de teclado…**.
- Faltaban claves de i18n para el diálogo de atajos y para `TextCaptureDialog` en los 4 bundles y en `messages_en.properties`; ahora están completas.
- `TextCaptureDialogTest` tiene una deuda documentada: su fixture de captura no demuestra el comportamiento real de `TextCaptureDialog`; ver el comentario del propio test.

## Condición de reanudación

Si la sesión se interrumpe, basta leer este archivo y la sección de estado de
cada fase para saber:
- cuál fue la última fase que se estaba trabajando,
- qué quedo hecho,
- qué falta por hacer en esa fase,
- qué mejoras extra quedaron pendientes por decisión del usuario.

## Revisión y control de versiones

- Al final de cada fase: commit + push del avance con mensaje descriptivo en
  inglés.
- Antes de comenzar una nueva fase: confirmar con el usuario que la fase
  anterior es aprobada.
- No avanzar a la siguiente fase sin que el usuario diga “pasa a la siguiente”
  o equivalente.

## Referencias

- Recomendaciones base: [`docs/adiciones-propuestas.md`](adiciones-propuestas.md)
- Arquitectura y donde va cada cosa: [`docs/architecture.md`](architecture.md)
- Plan de sesiones y estado del proyecto: [`PLAN.md`](../PLAN.md)
- Índice de documentación interna: [`docs/README.md`](README.md)
