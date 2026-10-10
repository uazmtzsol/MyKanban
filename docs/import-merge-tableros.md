# 🔀 Importar un tablero que ya existe: fusión (merge) en vez de duplicado

> **Estado: PROPUESTA — pendiente de conversación con el usuario.**
> No hay nada implementado. Esta nota existe para (1) no perder la idea,
> (2) llegar a la conversación con los detalles técnicos ya verificados y
> (3) tener una propuesta concreta sobre la mesa, inspirada en cómo lo
> resuelven otros programas.
> Fecha: 2026-10-08 · Origen: petición del usuario al cerrar la revisión
> de pendientes.

## La idea, en palabras del usuario

> «La idea es que cuando se importe un tablero cuyo nombre (¿quizás
> identificador?) sea uno que ya existe, se haga un merge, donde las
> tarjetas existentes en el tablero importado, que no se encuentran en la
> base de datos que las importa, se añadan, y las que ya existan, si son
> iguales, no haga nada, si son diferentes, si están en diferentes
> columnas, consulte la columna en que deberá quedar, y en cuanto al
> contenido, que indique cómo mezclar/actualizar la tarjeta.»

Resumen del pedido: **unión por identidad, sin duplicar**, y decisión del
usuario solo donde hay conflicto real (columna y contenido).

## Qué hace hoy la app (verificado en código, no supuesto)

- **Export**: `BoardService.exportBoard` escribe
  `{ "payload": { "boardId": <uuid>, "name": <nombre visible>, "board": <BoardMemento> } }`
  ([BoardService.java:895-910](../src/main/java/com/personalkanban/application/BoardService.java#L895)).
  O sea: **el archivo ya trae las dos cosas que el usuario dudaba** —
  identificador *y* nombre—. No trae versión de formato ni fecha de export.
- **Import**: `BoardService.importBoard` **siempre crea un tablero nuevo**
  con nombre único por sufijo —`Tablero`, `Tablero (2)`, `Tablero (3)`—
  ([BoardService.java:912-936](../src/main/java/com/personalkanban/application/BoardService.java#L912)).
  Nunca compara con lo existente ni fusiona. Importar dos veces el mismo
  archivo produce dos tableros, que es exactamente el dolor que la idea
  quiere resolver.
- **Identidad ya estable**: los ids son UUID generados una sola vez
  (`Board.java:45`, `Ids.java:15/19`, `ChecklistItem.java:35`,
  `EntryId.java:14`) y viajan dentro del archivo. Al reimportar, las
  tarjetas conservan su `CardId`; por eso **emparejar por id es viable hoy,
  sin migración ni cambio de formato**.
- **El motor de fusión ya existe**: `SyncMerge.merge(base, local, remote)`
  ([SyncMerge.java:66](../src/main/java/com/personalkanban/application/sync/SyncMerge.java#L66))
  hace exactamente el tipo de merge que se pide: campo por campo,
  colecciones por unión (etiquetas, checklist), timeline y enlaces por
  unión, «la edición gana al borrado», orden posicional conservado, y
  devuelve `MergeOutcome(merged, conflicts)` con un `SyncConflict`
  (`entidad · campo · valor local · valor remoto`) por cada valor que hubo
  que descartar. El sync además **guarda el ancestro común** por tablero
  (`BASE_PREFIX + boardId`) y ya escribe una copia del perdedor en
  `<bdDir>/conflicts/` (§4.1 de [`sync-design.md`](sync-design.md)).
- **Ya hay un precedente de re-emparejado entre tableros**:
  `matchedProcessIdOnTarget` vuelve a casar procesos **por nombre** cuando
  una tarjeta cruza de tablero
  ([BoardService.java:944-953](../src/main/java/com/personalkanban/application/BoardService.java#L944)).
  Ese criterio es reutilizable para columnas y procesos en el merge.

**Conclusión técnica**: lo que falta no es el motor de mezcla de contenido
(está hecho y probado con tests de propiedades), sino (a) **decidir la
identidad** del tablero y de la tarjeta, (b) **la interfaz** que recoge las
decisiones y (c) el camino «sin ancestro común», que es un merge a 2 bandas
y por eso necesita preguntar.

## Cómo lo resuelven otros programas

Cuatro patrones consolidados; conviene tomar uno de cada:

1. **Asistente de importación con vista previa, recuento y «aplicar a
   todos»** — *Anki* («Update existing notes: always / never / ask»),
   *Outlook* y *Thunderbird* (importación de libreta de direcciones con
   detección de duplicados y acción por fila), *Music/iTunes*. Lección: el
   usuario decide **una vez con el resumen delante** («12 nuevas, 3
   idénticas, 4 en conflicto»), no tarjeta por tarjeta.
2. **Resolución campo por campo con radios y panel comparado** —
   *JOSM/OSM* (diálogo de conflictos: por cada campo, radio A/B/ambos),
   herramientas de merge de Git (*KDiff3*, *P4Merge*, *Beyond Compare*) y
   el *Merge Editor* de VS Code. Lección: donde hay conflicto real, mostrar
   **los dos valores lado a lado** y ofrecer «todo el mío / todo el suyo»
   como botones de atajo. Y que **el ancestro común es lo que permite
   decidir sin preguntar**.
3. **Identidad por clave estable, no por nombre** — iCal/CalDAV
   (`UID` idéntico ⇒ *update*, `UID` nuevo ⇒ *insert*), Joplin/Evernote
   (cada nota con su GUID en el ENEX), Git (el blob no importa, importa el
   objeto). Lección: el **id decide**, el **nombre solo sirve para que la
   persona entienda la pregunta**.
4. **Conservador: duplicar siempre y no fusionar** — importaciones de
   *Notion*, *Evernote*, *Trello*: crean copia siempre; los gestores de
   contraseñas conservan ambas con sufijo. Lección: hace falta mantener una
   **puerta de salida** («importar como copia», el comportamiento actual)
   para cuando el usuario no está seguro.

**Patrón recomendado para este proyecto**: 3 + 1 + 2, con 4 como escape.
Identidad por id → vista previa con recuentos → radios por campo solo en
los conflictos reales → y el merge automático (`SyncMerge`) cuando hay
ancestro, que es el caso que ya trae el sync.

## Propuesta de diseño (a discutir, no aprobada)

### 1. Detección al elegir el archivo

Leer el `payload`, comparar contra el catálogo y resolver a uno de tres casos:

| Caso | Señal | Propuesta |
|---|---|---|
| **A** | `boardId` existe en el catálogo | Proponer **fusionar con ese tablero** (es el mismo tablero, seguro) |
| **B** | No coincide ningún id, pero el `name` coincide (sin distinguir mayúsculas/espacios) | **Preguntar**: ¿fusionar con «X» o importar como copia? (puede ser un renombrado, o una reexportación desde otro equipo) |
| **C** | Ninguna coincidencia | Comportamiento actual: tablero nuevo |

Regla dura: **nunca fusionar en silencio por coincidencia de nombre**. El
caso B siempre pregunta, y la caja de diálogo muestra nombre + fecha de
creación de ambos lados para que el usuario distinga.

### 2. Vista previa antes de escribir nada

Calcular el resultado **sin tocar la base de datos** y mostrar el resumen:
*qué se añade, qué es idéntico, qué entra en conflicto* (con la lista de
conflictos navegable, como en el asistente de Anki). Nada se escribe hasta
confirmar, y la escritura es **una sola transacción deshacible** (un
memento), de forma que un Ctrl+Z revierta la importación entera, igual que
el resto de la app.

### 3. Emparejado por tarjeta

1. **Por `CardId`** (lo normal: el id viaja en el archivo).
2. Si el archivo no trae id (archivo antiguo o editado a mano), por
   **título normalizado** (trim, minúsculas, espacios colapsados) y **solo
   si es único**; con dos o más candidatos, se pregunta.

### 4. Reglas por tarjeta (lo que pidió el usuario, caso por caso)

| Situación | Acción |
|---|---|
| Está en el archivo y no aquí | **Añadir** a su columna (con su checklist, notas, vencimiento, etiquetas, proceso y registros de tiempo) |
| Está aquí y no en el archivo | **Se queda** (importar nunca borra) |
| Idéntica | **Nada** |
| Diferente, misma columna | Preguntar **cómo mezclar el contenido** |
| Diferente, distinta columna | Preguntar **en qué columna queda** *y* cómo mezclar el contenido (la idea del usuario) |

«Idéntica» debe significar **todos los campos que viajan en el archivo**
(título, descripción, notas, etiquetas, checklist, vencimiento, color,
proceso, columna, posición), no solo el título.

Interfaz propuesta para el contenido: una fila por tarjeta con **dos
columnas (local / archivo)** y, por campo, un radio *mío / del archivo*;
más tres atajos: «mantener el mío», «usar el del archivo» y «combinar
automáticamente», este último habilitado **solo si hay ancestro** (ver
siguiente punto). Y un «aplicar a todas las restantes» para no repetir 20
veces la misma decisión.

### 5. El atajo automático: reutilizar `SyncMerge`

`SyncMerge` ya resuelve el contenido sin preguntar cuando hay **base**
(ancestro común): cambios en campos distintos se conservan, y solo los
edits simultáneos al mismo campo quedan como `SyncConflict`. Dos vías para
conseguir esa base:

- **Vía sync**: si el tablero está sincronizado, la base ya está guardada
  en ajustes (`BASE_PREFIX + boardId`). Es el caso ideal: merge automático
  + lista de conflictos reales para preguntar.
- **Vía archivo**: añadir al export un `formatVersion`, un `exportedAt` y,
  opcionalmente, el **snapshot base** del tablero. Eso convierte cualquier
  export en un archivo «merge-friendly» sin depender del sync. Cambio de
  formato compatible hacia atrás (los archivos viejos simplemente no traen
  los campos nuevos).

Sin base (importar un archivo sobre una BD que nunca sincronizó), el merge
es a **2 bandas** y toda diferencia es una pregunta: por eso la vista previa
es indispensable.

### 6. Reglas estructurales (lo que no es una tarjeta)

- **Columnas**: casar por `ColumnId`, luego por título normalizado; una
  columna que solo trae el archivo se **crea** (al final, o en el orden del
  archivo). La marca de «columna final/hecho» es del tablero: si difiere,
  **se pregunta una sola vez**, no por tarjeta.
- **Procesos**: casar por nombre, reutilizando el criterio que ya existe
  para tarjetas que cruzan de tablero.
- **Enlaces de precedencia**: unión; se descartan los que apunten a
  tarjetas que no existen en el resultado.
- **Registros de tiempo**: unión por `EntryId`; **nunca se pierde un
  registro**. En conflicto (mismo id, datos distintos), el más reciente
  gana y se conserva el otro en la copia.
- **Checklist**: unión por id de ítem; un mismo ítem con distinto
  `hecho/no hecho` es un conflicto de campo y se pregunta (con «la edición
  gana al borrado» ya como regla del motor).

### 7. Seguridad

Antes de fusionar: **copia de seguridad automática** del tablero afectado
(JSON con fecha, en la carpeta de datos) y, si hubo conflictos resueltos
por el motor, copia de la versión perdedora en `conflicts/`, siguiendo el
precedente de §4.1. Fusionar nunca debe poder perder datos sin rastro ni
sin deshacer.

## Preguntas abiertas (esto es lo que hay que conversar)

1. **Identidad**: ¿basta el `boardId`? ¿Preguntamos también en el caso B
   (mismo nombre, distinto id) o directamente se importa como copia?
2. **Cuándo preguntar**: ¿solo en conflicto real (recomendado) o siempre
   con la decisión «aplicar a todas» a mano?
3. **Tarjetas sin id** (archivos viejos o editados a mano): ¿emparejar por
   título cuando es único, o añadir siempre como nuevas?
4. **Tarjetas que aquí existen y en el archivo no**: ¿se quedan (recomendado)
   o se ofrece la opción de eliminarlas/archivarlas?
5. **Columna distinta**: ¿preguntar siempre (como pide el usuario), o
   mantener la local por defecto con opción de cambiarla? ¿Y si la columna
   del archivo no existe aquí — se crea o se ofrece una existente?
6. **Qué es «igual»**: ¿todos los campos del archivo (recomendado) o solo
   título y descripción?
7. **Dónde se aplica**: ¿merge en el sitio sobre el tablero existente, o
   crear un tablero temporal con el resultado para revisarlo y confirmar
   después? (La segunda es más segura y encaja con la vista previa; la
   primera es más directa.)
8. **Sync**: si el sync está activo, ¿la fusión se empuja al servidor de
   inmediato o espera al siguiente «Sincronizar ahora»?
9. **Memoria de decisiones**: ¿se recuerda la decisión por tarjeta entre
   importaciones, o se pregunta cada vez?
10. **Alcance del archivo**: hoy el export es del tablero activo. ¿Se
    piensa también en fusionar bases completas o importaciones de otros
    formatos (CSV/Trello/etc.), o solo el formato propio?

## Encaje técnico y de arquitectura (cuando se implemente)

- **El plan se calcula sin UI**: un caso de uso puro en `application/` que
  devuelva un *plan* (lista de decisiones: añadir / nada / preguntar
  columna / preguntar campo a campo) a partir de (archivo, tablero local,
  base opcional). Testeable entero sin JavaFX, que es donde está el valor.
- **La UI solo lo pinta**: un diálogo en `ui/` que muestra la vista previa y
  recoge las decisiones; ningún cálculo en la vista.
- **Aplicar es un comando deshacible** (memento), no una escritura suelta.
- **i18n en los 5 bundles** (base + EN/ES/DE/FR) y las claves nuevas antes
  de tocar la UI; lo exige `I18nCoverageTest`, y `ArchitectureTest` marca
  dónde puede vivir cada pieza.
- **Nada de dependencias nuevas**: el parseo ya es Jackson y el merge ya
  está escrito.

## Fuera de alcance de esta idea

- No se reescribe `SyncMerge` ni el diseño de sync.
- No se introducen *fractional index* ni lápidas (los conflictos de orden
  posicional siguen siendo deuda conocida del sync).
- No hay fusión **silenciosa** sin copia de seguridad ni sin deshacer.
- No se versiona el formato de export sin discutirlo primero (el
  `formatVersion` propuesto es aditivo y compatible hacia atrás).

## Primer paso sugerido cuando el usuario lo pida

Empezar por el **caso A** (mismo `boardId`, archivos producidos por esta
misma app), que es el escenario real de «reimporto mi tablero y no quiero
duplicarlo»: detección + vista previa + unión por id + preguntas de columna
y contenido. El resto (caso B, merge a 2 bandas sin base, formatos ajenos)
se añade después sobre esa misma base.
