# Sincronización con una base de datos externa — plan de diseño

> Estado: **MVP implementado** (cliente Java + API PHP/MySQL propia,
> sincronización por snapshot completo con bloqueo optimista). Objetivo:
> trabajar con el mismo tablero desde varias computadoras, con la base
> local como fuente de verdad y sincronización cuando hay red, al estilo
> Zotero.

## 0. MVP implementado (2026-10-05)

Decisión del usuario: **API PHP + MySQL propio** (tiene hosting con
MySQL donde puede subir scripts PHP) en vez de los BaaS de §5, con
 granularidad **MVP de tablero completo ahora, deltas después**.

- **API** (`sync/`, un solo archivo `index.php` sin framework):
  `GET ?action=get&board_id=<uuid>`, `POST ?action=put`,
  `GET ?action=catalog`. Auth por cabecera `X-API-Key` (el hash
  SHA-256 vive en `config.php`, fuera del repo). Escritura optimista:
  el push lleva `base_version`; un push desfasado responde **409 con
  el board remoto** para fusionar; `base_version: -1` fuerza el
  sobreescrito. Cada escritura aceptada sube la versión y estampa el
  reloj del servidor (nunca los relojes del cliente). `setup.sql`
  crea `sync_board` + `sync_meta`; `config.sample.php` documenta
  `max_payload_bytes` (5 MB).
- **Cliente Java**: puerto `SyncRepository` (`application.port`,
  `fetch`/`push`/`catalog`) + DTOs `RemoteBoard`/`RemoteBoardInfo` +
  `PushResult` sellado (`Ok` | `Conflict` con el remoto) +
  `SyncException` (Kind + statusCode) — todos en `application` porque
  ArchUnit prohíbe que application dependa de infrastructure.
- **Adaptador** `HttpSyncRepository` (`infrastructure.sync`):
  `java.net.http.HttpClient` del JDK (cero dependencias nuevas) +
  `BoardJsonMapper`; el payload es el mismo JSON del undo y del
  export/import. Traduce 401/400/413/409/5xx a excepciones tipadas.
- **Wiring** en `AppContext`: settings `sync.server.url` y
  `sync.api.key` por base de datos; `configureSync(url, key)` y
  `syncRepository()`. Sin URL o sin clave → sync desactivado y la app
  arranca igual (local-first, §1).
- **Tests:** `HttpSyncRepositoryTest` (9, con un servidor HTTP del JDK
  que imita el contrato de la API) y `PhpSyncApiEndpointTest` (e2e
  real contra PHP + MariaDB; se autosalta si el servidor no está).
  Suite: **203/203 OK**.

Camino a deltas (fase siguiente): migración V10 con `updated_at`,
lápidas y token de sync; outbox por entidad (§3.1) y cursores
(§3.2) para el ciclo pull→merge→push de §3.3 — el puerto ya lo
soporta sin tocar dominio ni UI.

## 1. Principios

1. **Local-first.** La app funciona 100 % sin red. SQLite (`kanban.db`) sigue
   siendo la fuente de verdad local; la nube es un espejo, nunca un requisito.
2. **IDs generados por el cliente.** Ya usamos UUID en `BoardId`, `ColumnId`,
   `CardId`, `ProcessId`, `EntryId`. Esto elimina el problema más difícil de
   la sincronización (remapear IDs asignados por el servidor). No hay que
   cambiar el modelo de identidad.
3. **Sincronización por cambios, no por snapshots completos.** Subir el
   `BoardMemento` entero en cada sync no escala ni resuelve conflictos;
   necesitamos un *registro de cambios* (outbox) por entidad.
4. **Reconciliación determinista.** Ante ediciones concurrentes, la
   resolución debe ser reproducible en cualquier máquina (misma entrada →
   mismo resultado), sin depender del orden de llegada.
5. **Confianza.** Transporte TLS; credenciales fuera del repo (almacén del
   sistema o settings cifrado); opción de cifrar el blob remoto.

## 2. Estado actual aprovechable

- `BoardMemento` ya es el DTO canónico (columnas + tarjetas + procesos +
  enlaces + timeline) y es serializable a JSON con `BoardJsonMapper`.
- `BoardExport`/`importBoard` ya exportan e importan un tablero a JSON.
- `SqliteBoardRepository` guarda en una transacción por comando y el
  `BoardService` es *persist-first* con undo (memento). Cada comando ya
  produce exactamente un cambio lógico → punto natural para registrar el
  outbox.
- Arquitectura hexagonal: añadir un puerto `SyncRepository` + adaptador
  HTTP encaja sin tocar dominio.

## 3. Modelo de sincronización propuesto

### 3.1 Registro de cambios local (outbox)

Al confirmar cada comando, además de `save`, se anota el objeto tocado:

```sql
CREATE TABLE sync_object (
    entity_type TEXT NOT NULL,          -- board|column|card|checklist_item|link|process|timeline_entry
    entity_id   TEXT NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0,  -- versión remota que ya tenemos (0 = nunca subido)
    updated_at  INTEGER NOT NULL,       -- epoch ms del último cambio local
    device_id   TEXT NOT NULL,
    deleted     INTEGER NOT NULL DEFAULT 0,  -- lápida (tombstone)
    PRIMARY KEY (entity_type, entity_id)
);

CREATE TABLE sync_outbox (
    seq         INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type TEXT NOT NULL,
    entity_id   TEXT NOT NULL,
    op          TEXT NOT NULL,          -- upsert|delete
    payload     TEXT,                   -- JSON del objeto en ese momento
    created_at  INTEGER NOT NULL
);

CREATE TABLE sync_meta (
    key TEXT PRIMARY KEY, value TEXT    -- device_id, cursor, last_sync_at
);
```

- `version` = cursor remoto del objeto (para detectar si la nube cambió desde
  nuestra última lectura). Un objeto nunca subido tiene `version = 0`.
- Las **lápidas** son imprescindibles: si solo subimos altas/ediciones, un
  borrado en la máquina A “resucita” al descargar de B.

### 3.2 Modelo remoto (lado servidor)

```sql
CREATE TABLE remote_object (
    entity_type TEXT NOT NULL,
    entity_id   TEXT NOT NULL,
    version     BIGINT NOT NULL,        -- secuencia monótona por biblioteca
    deleted     BOOLEAN NOT NULL,
    updated_at  BIGINT NOT NULL,
    device_id   TEXT NOT NULL,
    payload     JSON,
    PRIMARY KEY (entity_type, entity_id)
);
-- cada escritura toma nextval(library_seq) y lo guarda en version
```

- **Cursor de pull**: `GET /objects?since=<cursor>` devuelve todo lo que tenga
  `version > cursor`, incluidas lápidas. El cursor es el `max(version)` visto.

### 3.3 Algoritmo de ciclo (por tablero)

1. **Pull**: pedir cambios desde el cursor; aplicar entidad por entidad con la
   política de conflicto (§4); guardar el nuevo cursor.
2. **Merge local**: reconstruir el `Board` con `Board.restore` y `persist()`
   para que la BD local quede igual a lo acordado.
3. **Push**: enviar el `sync_outbox` agrupado por objeto (el último gana);
   el servidor responde con la `version` aceptada o “conflict”.
4. **Conflictos**: resolver en cliente (§4) y reintentar push; actualizar
   `sync_object.version`.
5. **Éxito**: vaciar el outbox hasta el `seq` confirmado y guardar
   `last_sync_at`.

El ciclo es **idempotente** y reanudable: si se corta la red a mitad, se
repite sin efectos dobles.

## 4. Política de conflictos

Es una app **personal** (un usuario, varias máquinas), así que el caso
dominante es “ediciones separadas en el tiempo”, no “dos personas a la vez”.
Aun así definimos reglas deterministas:

| Tipo de dato | Estrategia |
|---|---|
| Campos escalares (título, descripción, color, fecha, notas, proceso) | **Last-Writer-Wins por campo** usando `updated_at`; empate → mayor `device_id` (determinista) |
| Colecciones (`labels`, checklist) | **Unión por id** de ítem; borrado gana si hay lápida |
| **Timeline** (`TimelineEntry`) | **Unión append-only**: entradas inmutables, sin conflicto posible; borrado gana con lápida |
| Enlaces de precedencia (`CardLink`) | Unión de pares; borrado gana con lápida |
| **Orden** (columnas, tarjetas dentro de columna) | Clave de orden explícita (**fractional index** o enteros dispersos) para que insertar en medio no renumere todo y no genere falsos conflictos |

El “reloj de reconciliación” del borrador original se concreta como
`(updated_at, device_id)` por objeto más versión remota: no hace falta un
reloj vectorial completo porque el modelo es de un solo autor.

**LWW por campo** (en vez de por registro) evita el escenario típico: editar
la descripción en la laptop y el color en el escritorio no debe perder uno de
los dos cambios.

### 4.1 UI de conflictos

Como Zotero: cuando una resolución pierde información relevante (p. ej. dos
ediciones de la misma descripción), guardar la versión perdedora como
**copia de conflicto** y mostrar un aviso discreto, en lugar de descartarla
en silencio.

## 5. Alternativas de backend (planes gratuitos vigentes)

Datos consultados en **octubre de 2026**; los planes gratuitos cambian, hay
que revalidarlos antes de implementar.

| Opción | Modelo | Free tier (aprox.) | Encaje con este proyecto |
|---|---|---|---|
| **Cloudflare D1** (+ Workers) | SQLite | 5 GB, 5 M filas leídas/día, 100 K escritas/día | **Muy alto**: el esquema local ya es SQLite; casi se reutiliza el SQL |
| **Turso (libSQL)** | SQLite | 100 BD / 5 GB, 500 M lecturas/mes | **Muy alto**: SQLite con replicación; ojo con su reescritura en curso |
| **Supabase** | PostgreSQL + Auth + REST/RLS | 500 MB BD, 50 K usuarios activos, 5 GB egreso | **Alto**: Postgres ideal para versionado y RLS; hay que traducir esquema |
| **Firebase / Firestore** | Documental | 1 GiB, 50 K lecturas/día, 20 K escrituras/día | Medio: sincronización fácil, pero bloqueo de proveedor y límites de consultas |
| **Neon** | PostgreSQL serverless | ~0,5 GB | Alto, similar a Supabase sin Auth integrado |
| **Google Drive** | Archivos (blob) | 15 GB | **Fallback sin backend**: guardar 1 JSON por tablero (+ outbox) |

> Nota sobre “¿se puede usar gratis en Google?”: sí, de dos formas.
> **Firestore** tiene plan Spark gratuito (1 GiB, 50 K lecturas/día,
> 20 K escrituras/día; al superarlo la API deja de responder hasta el día
> siguiente). **Google Drive** es más simple todavía: 15 GB gratis para
> guardar el archivo de sincronización, sin servidor propio.

### 5.1 Recomendación

> **Decisión (2026-10-05):** se eligió **API PHP + MySQL propio**
> (opción no tabulada arriba: el usuario tiene hosting con MySQL).
> Ventajas frente al plan original: cero dependencia de OAuth o de
> terceros, control total del esquema y del límite de payload, y el
> mismo wire protocol sirve para el MVP (snapshot) y para los deltas
> futuros (endpoints `?since=`). Desventaja: hay que mantener el
> script y la BD remota; el cifrado en reposo y el TLS terminan en
> el servidor propio (§5).

1. **Para el primer prototipo (menor esfuerzo): Google Drive como blob.**
   Sin backend que mantener: un archivo JSON por tablero con
   `{cursor, objects[]}` y el outbox local. OAuth de escritorio de Google.
   Suficiente para un usuario y varias máquinas.
2. **Para la versión “buena”: SQLite remoto (Cloudflare D1 o Turso).**
   Permite sync por cambios con cursores y reutiliza casi el mismo SQL.
3. **Si se quiere multiusuario/colaboración: Supabase (Postgres + RLS).**

Evitar Firestore salvo que se priorice “tiempo real”; el modelo documental
complica la reconciliación por campo y ata al proveedor.

## 6. Plan por fases

### Fase 1 — Andamiaje local (sin red, sin riesgo)

> **Pendiente.** El puerto `SyncRepository` y su adaptador HTTP ya
> existen (§0), pero el outbox de §3.1 (migración V10: `sync_object`,
> `sync_outbox`, `sync_meta`, `device_id`) aún no.

- Migración V10: `sync_object`, `sync_outbox`, `sync_meta`; `device_id`
  generado una vez y guardado en `sync_meta`.
- En `BoardService`, al final de `finishTransaction`, registrar el cambio en
  el outbox (a partir del diff entre `before` y el memento nuevo) — o, más
  simple, marcar el tablero completo como “dirty” al principio.
- Tests de que el outbox refleja altas/ediciones/borrados.

### Fase 2 — Backend mínimo

> **Hecho (2026-10-05):** API PHP (`sync/index.php`) + esquema remoto
> (`sync/setup.sql`) + auth por `X-API-Key` con hash en `config.php`.
> La API actual es de snapshot completo; los endpoints `?since=` de
> §3.2 llegan con la Fase 1 (deltas).

- (Hecho) API mínima: `GET ?action=get`, `POST ?action=put`,
  `GET ?action=catalog`.
- Pendiente: `GET /objects?since=<cursor>` y `POST /objects` (lote)
  para sincronización por cambios.

### Fase 3 — Motor bidireccional
- Implementar el ciclo pull → merge → push con conflictos (§3.3, §4).
- Botón **Sincronizar** + estado (última sync, pendientes, errores) en la UI.
- Reusar `BoardJsonMapper` para el payload; `Board.restore` para aplicar.
- Pendiente también: UI para pedir URL/clave (diálogo de preferencias)
  y resolver el 409 (§4.1: copia de conflicto).

### Fase 4 — Robustez
- Reintentos con backoff, sync en segundo plano, detección de offline.
- Lápidas con retención (purgar tombstones antiguos).
- Adjuntos (imágenes de fondo) opcionalmente en un bucket/Drive, al estilo
  del “file storage” de Zotero.

### Fase 5 — Multi-dispositivo real
- Pruebas con dos instalaciones sobre la misma biblioteca.
- UI de conflictos (§4.1) y de “restaurar versión”.
- Cifrado en reposo opcional del blob remoto.

## 7. Riesgos y decisiones abiertas

- **Orden**: hoy el orden es un entero `position` reescrito en cada `save`.
  Para sync conviene migrar a *fractional index* (clave de orden estable).
- **undo/redo**: el historial JSON es local; no debe sincronizarse.
- **Imágenes de fondo**: referencias a rutas locales no viajan; decidir si se
  suben al backend o se excluyen.
- **Proveedor**: D1/Turso/Supabase cambian sus planes; mantener el puerto
  `SyncRepository` para poder cambiar de backend sin tocar dominio ni UI.
- **Privacidad**: valorar cifrado de extremo a extremo del payload si se usa
  un servicio de terceros.

## 8. Qué NO hacer

- No sincronizar el `kanban.db` completo por archivo compartido (Drive/Dropbox
  sobre el `.db`): con WAL y dos procesos se corrompe. Solo el archivo JSON de
  intercambio, nunca la base viva.
- No subir snapshots completos como única estrategia: pierde cambios
  concurrentes y no borra.
- No introducir dependencias de red en el dominio ni en la aplicación: todo
  pasa por el puerto `SyncRepository`.
