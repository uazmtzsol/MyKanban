# Freebuff Desktop — design notes

## Vista y manejo de procesos (Procesos)

Los procesos son un conjunto de tarjetas con la misma etiqueta. Las etiquetas
de los procesos comienzan con "#" (ej. "#compras"). Al ingresar etiquetas
manualmente **no se permite el "#" al inicio**: se valida con
`LabelConventions.startsWithHash` y `LabelConventions.split`.

### Cambio de vistas (Kanban ↔ Procesos)
Se aceptan sugerencias. Se mantiene la barra de filtro por proceso
(`ComboBox` en el `filter-bar` de `BoardController`), y el menú
**Board → Board/Procesos** permite crear la vista de "Procesos" desde el
estado actual del tablero.

### Vista "Procesos"
- No hay columnas; cada columna kanban se convierte en un **contenedor
  de tarjetas**.
- Las tarjetas se muestran de **izquierda a derecha**, unidas por flechas
  `Tarjeta1 → Tarjeta2 → TarjetaN`.
- Si hay varias tarjetas, se muestran como **renglones**: primero un proceso
  (contenedor), abajo otro proceso, etc.
- Las tarjetas tienen la forma:

```text
----------------------------
|  <- |               | -> |
----------------------------
|                          |
|                          |
|                          |
----------------------------
|  <- |               | -> |
----------------------------
```

  - **Flechas superiores**: botones para añadir **nuevas tarjetas**
    (antecedentes: flecha a la izquierda; consecuentes: flecha a la derecha)
    de la tarjeta actual.
  - **Flechas inferiores**: añaden antecedentes/consecuentes **elegiendo una
    tarjeta existente** de la lista.
- En la vista gráfica de procesos se pueden **seleccionar las flechas**
  que enlazan las tarjetas y **ponerles notas**. Se deja abierta la
  posibilidad de más atributos y acciones con las flechas.

### Estado de los procesos
- Los procesos y la asignación de tarjetas a procesos ya existían en
  **sesión 4.6** (`Board.processes`, `card.processId()`, `cardView`/`process
  filter`, `addProcess/renameProcess/removeProcess`, `linkCards/unlinkCards`
  con ciclo acíclico). Esta sesión **no duplica** esa capa: aprovecha el
  modelo ya consolidado.
- Las tarjetas **predecesoras** y **posteriores** se calculan desde el
  grafo de precedencias (`Board.incomingPredecessorsOf` /
  `outgoingSuccessorsOf`) y de las etiquetas de proceso.

## Time tracking

- En la vista de detalle de una tarjeta hay un botón toggle con forma de
  reloj/cronómetro.
- Al pulsar:
  - **Inicio**: se añade un registro de tiempo actual (inicio de seguimiento)
    para la tarjeta.
  - **Clic de nuevo**: se cierra el registro añadiendo el momento de fin.
- Se pueden tener **muchos registros de tiempo** por tarjeta (historial).
- Cada **inicio de registro** añade un **nuevo registro de comentario**.
  Necesitamos un editor simple para comentarios.
- Ejemplo: inicio el 1/01/2027 a las 13:00, se escribe "*Iniciando el
  registro de tareas *" y al cerrar se guarda la hora de fin (`Y`).
- El campo de comentario **después de quitar espacios/en blanco y
  tabulaciones, si tiene texto "real", se guarda como comentario**; si está
  vacío, no se guarda comentario.
- **No se permiten actualizar** los registros de tiempo. Solo se pueden
  **borrar**, no editar hora de inicio, fin ni comentario.

## Editor Markdown

- Componente para manejo de texto markdown.
- Vista edición: se muestra el texto en formato markdown.
- **Toggle button** (forma de lápiz) cambia al modo "vista": se muestra el
  texto ya renderizado, sin ver el código markdown.
- El editor se usa en **toda ventana** donde se necesita texto más allá de
  una línea: descripción de tarjeta, comentarios de tarjeta, comentarios de
  registros de tiempo, etc.

## Sincronización con una base de datos externa

Se planea cómo sincronizar la base de datos local con una externa, de la
manera de Zotero (base de datos local + sincronización cuando hay red).
Esto permite trabajar con el mismo programa desde distintas computadoras.

## Etiquetas de proceso y validación

- Las etiquetas de los procesos comienzan con "#", pero **no se permite el
  "#" al inicio de etiquetas ingresadas manualmente**.
- Se valida con `LabelConventions`:
  - `startsWithHash(label)`: true si la etiqueta empieza con "#".
  - `split(raw)`: divide por espacios, comas o punto y coma y devuelve
    `List<String>` de etiquetas válidas.

## Clave importante: cómo se sincroniza Zotero (base de datos local)

Zotero tiene una base de datos local y se sincroniza con una base externa
cuando hay red. Para el este programa:

1. **Base de datos local**: el archivo SQLite `kanban.db` (ya existe).
2. **Base de datos externa**: un archivo/nodo remoto (ej. SQLite en un
   servidor cloud, o un archivo JSON/SQLite compartido).
3. **Sincronización**:
   - Cambios locales → exportan al formato de intercambio local (ya existe:
     `BoardExport` JSON + `BoardMemento`).
   - Cambios remotos → importan el mismo formato.
   - Se comparan `BoardMemento`, y se aplican las diferencias sin cacheado.
4. **Iteración**: la aplicación se ejecuta sin red, genera cambios, los
   sincroniza al ejecutarse con red. Para evitar conflictos, se usa un
   **reloj de reconciliación** que discrimina cambios locales por el
   `CardId`/`EditorId` y las versiones de contenido.

**Clave**: el contraste se hace por `CardId`/`EditorId` y el *contenido*
operacional (no por `CardId` + `position` que pueden variar entre
instancias), evitando campos que cambian por la "estructura" de la tabla.

## Resumen de entidades nuevas

- `Timeline`, `TimelineEntry`, `EntryId` — time tracking.
- `LabelConventions` — validación de etiquetas y split.
- `CardViewBuilder` / `ProcessView` — la vista de procesos con flechas.
- `Card` con `timeline()` (new) y el detalle de tarjeta con el toggle.
- `MarkdownEditor` — componente reutilizable para edición/vista.
- `SyncController` — plan de sincronización con una base externa.
