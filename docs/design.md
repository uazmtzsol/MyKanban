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

➡️ **El plan completo vive en [sync-design.md](sync-design.md)**: modelo de
outbox y lápidas, política de conflictos por campo, comparativa de backends
gratuitos (Cloudflare D1, Turso, Supabase, Firestore, Google Drive) y plan
por fases.

## Etiquetas de proceso y validación

- Las etiquetas de los procesos comienzan con "#", pero **no se permite el
  "#" al inicio de etiquetas ingresadas manualmente**.
- Se valida con `LabelConventions`:
  - `startsWithHash(label)`: true si la etiqueta empieza con "#".
  - `split(raw)`: divide por espacios, comas o punto y coma y devuelve
    `List<String>` de etiquetas válidas.

## Cómo se sincroniza Zotero (referencia)

Zotero mantiene una base local y sube/baja cambios cuando hay red, con
identificadores estables y resolución de conflictos por versión. Nuestro
plan aplica las mismas ideas (ver [sync-design.md](sync-design.md)):
IDs generados por el cliente (ya es el caso), outbox de cambios, lápidas
para borrados y reconciliación determinista. El contraste se hace por
`CardId`/`EntryId` y el **contenido** (no por `position`, que varía entre
instancias).

## Resumen de entidades nuevas

- `Timeline`, `TimelineEntry`, `EntryId` — time tracking.
- `LabelConventions` — validación de etiquetas y split.
- `CardViewBuilder` / `ProcessView` — la vista de procesos con flechas.
- `Card` con `timeline()` (new) y el detalle de tarjeta con el toggle.
- `MarkdownEditor` — componente reutilizable para edición/vista.
- `SyncController` — plan de sincronización con una base externa.
