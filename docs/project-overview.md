# Visión general del proyecto

*Última actualización: 2026-09-29*

## Qué es

**Personal Kanban** — aplicación de escritorio offline para tableros kanban
personales. Es un "rebuild" en Java del proyecto web original
[nishantpainter/personal-kanban](https://github.com/nishantpainter/personal-kanban)
(React/Redux + localStorage). El usuario original es hispanohablante y usa la
app de forma personal (tableros como "uaz" sugieren trabajo con la Universidad
Autónoma de Zacatecas).

Todo el dato es local: un archivo SQLite. Nada sale de la máquina. Es una app
de escritorio JavaFX, **no** web.

## Stack

| Componente | Tecnología | Versión |
|---|---|---|
| Lenguaje | Java | 21 (release en pom.xml) |
| UI | JavaFX (javafx-controls + javafx-web para el WebView de markdown) | 21.0.12 |
| Persistencia | SQLite vía JDBC (xerial) | 3.53.4.0 |
| JSON (export/import + undo history) | Jackson databind + jsr310 | 2.18.2 |
| Markdown | commonmark-java + ext GFM tables/strikethrough | 0.24.0 |
| Exportar a PDF | Apache PDFBox (snapshot del tablero paginado) | 3.0.3 |
| Build | Maven | 3.9+ |
| Tests | JUnit 5, AssertJ, ArchUnit (reglas de arquitectura) | — |

## Comandos esenciales

```bash
mvn javafx:run          # correr en desarrollo
mvn test                # suite de tests (dominio+servicio+sqlite+archunit+i18n+ui-puro)
mvn -Pportable package -DskipTests   # fat jar ~140MB en target/personal-kanban.jar
```

Paquete portable listo para copiar: `target/portable/` = jar + kanban.bat +
kanban.sh + LEEME.txt (se regenera tras cada build copiando los launchers;
target/ es gitignore). Smoke test del jar: proceso vivo, BD creada en
`-Dpk.data.dir`, sin errores. El warning "classes were loaded from 'unnamed
module'" es normal en fat jars de JavaFX.

⚠️ **Nota de entorno (máquina actual):** el repo local de Maven NO tiene las
dependencias JavaFX descargadas (un `mvn -o dependency:build-classpath` falla
por `org.openjfx:javafx-controls:21.0.12` no resuelto offline). Compilar/Probar
requiere conexión a Maven Central o que el usuario compile en su IDE. Lo que
SÍ está en el repo local: `sqlite-jdbc` (varias versiones) — suficiente para
experimentar con JDBC vía `jshell`. Java 21 (Temurin 21.0.9) está instalado.

## Estructura del repositorio

```
pom.xml                  # Maven, Java 21, perfil "portable" (shade)
kanban.bat / kanban.sh   # launchers portables (ver abajo)
data/                    # (vacía en el checkout; los launchers crean aquí la BD portable)
docs/                    # ESTA documentación interna
src/main/java/com/personalkanban/
├── AppContext.java      # composition root: wiring + ciclo de vida del archivo de BD
├── Launcher.java        # entry del fat jar (sin JavaFX module path)
├── Main.java            # JavaFX Application bootstrap
├── domain/              # Java puro: Board, BoardColumn, Card, value objects, eventos
├── application/         # BoardService (fachada), BoardCommand*, puertos (interfaces)
├── infrastructure/      # adaptadores SQLite (Database, SchemaMigrator, repos), JsonUndoHistory
└── ui/                  # JavaFX: BoardController, builders, Dialogs, I18n, ThemeManager, markdown/
src/main/resources/
├── css/light.css, dark.css          # temas (una hoja activa por escena)
├── db/migration/V1..V4__*.sql       # migraciones versionadas
└── i18n/messages*.properties        # 4 idiomas: en, es, de, fr
src/test/java/...        # tests por capa + ArchitectureTest (ArchUnit)
```

Capas con dependencia **solo hacia adentro**: `ui → application → domain`,
`infrastructure → application (puertos) → domain`. ArchUnit lo enforced en
`src/test/java/com/personalkanban/architecture/ArchitectureTest.java`:
- `domain` nunca importa JavaFX, JDBC ni capas externas
- `application` nunca importa JavaFX, JDBC ni `ui`
- `ui` nunca importa JDBC/SQLite/`infrastructure`

## Launchers portables y ubicación de datos

Los launchers `kanban.bat`/`kanban.sh` (en la raíz, junto al jar):
1. Buscan un JRE 21+ en `jre/` junto al script; si no, `java` en PATH.
2. Corren con `-Dpk.data.dir=<carpeta>/data` → **todo** (BD, historial undo,
   config) vive en `data/` junto al jar. USB-drive-portable, cero rastros en
   el host.

Sin launchers:
- Default: `~/.personalkanban/` (`kanban.db`, `history/*.json`, `config.properties`)
- Override: `-Dpk.data.dir=<folder>`
- La BD concreta en uso se recuerda en `config.properties` (clave `db.path`)
  junto con 5 recientes (`db.recent.0..4`).

La app puede cambiar de archivo de BD en caliente (menú *Database*):
`AppContext.openDatabase(path)` reconstruye Database+repos+servicio y
`BoardController.rebind()` reconstruye toda la UI. El undo history vive en
`history/` **junto al archivo de BD** (una BD = unidad autocontenida
movible/sincronizable).

## Funcionalidades ya implementadas (estado inicial heredado)

- Multi-tablero: catálogo, crear/renombrar/eliminar, último tablero activo
  persistido (`app_setting` clave `board.last`)
- Columnas con límite WIP (badge `n/límite`, rojo al llenarse), editar/eliminar/vaciar
- Tarjetas: título, descripción markdown, color (paleta de 8 + custom vía
  ColorPicker), fecha límite con badge de vencida, etiquetas (chips)
- Drag & drop: reordenar columnas; mover/reordenar tarjetas entre columnas con
  **slot de inserción visual** entre tarjetas (drop exacto donde indica la línea)
- Notas markdown: doble clic abre ventana no-modal con editor + preview
  WebView (JS deshabilitado por seguridad), botón **?** con guía rápida de
  sintaxis (tabla "Escribe → Resultado" renderizada en vivo); el frente de
  la tarjeta muestra un resumen nativo ligero (`MarkdownSummary`, máx 4 líneas)
- Export/import de un tablero como JSON portable; el chooser abre en la
  última carpeta usada (compartida por export/import; si no existe —USB
  retirada— cae a la carpeta personal del usuario)
- Undo/redo persistente (Ctrl+Z / Ctrl+Shift+Z), historial JSON junto a la BD
- Tema claro/oscuro persistido; 4 idiomas (EN, ES, DE, FR) con switch en caliente
- Filtro de tarjetas por etiquetas con modo AND/OR
- **Selección múltiple (P1.5)**: botón ☐ por columna → barra con acciones
  bulk sobre las tarjetas marcadas: etiquetas añadir/quitar, color, mover a
  otra columna (atómico con WIP), eliminar con confirmación; todo con undo
  en un paso
- **Personalización visual (P1.6)**: fondo con tinte del color elegido en
  columnas y tarjetas (legible en ambos temas), **colapsar columnas** a una
  tira vertical con el título (persistido por tablero), tarjetas modernizadas
  (esquinas, sombra, hover)
- **Nuevo tablero con plantilla**: Plantilla Kanban Estándar
  (Por hacer/Haciendo/Hecho, preseleccionada), columnas personalizadas
  (número o nombres por comas) o sin columnas
- El **nombre del tablero activo** siempre visible (badge en toolbar +
  título de ventana)
- Errores con causa raíz visible; rollback en memoria si un guardado falla
  (la UI nunca diverge de la BD)

## Lo que NO existe aún (pedido por el usuario, ver roadmap)

Modos de vista de tarjeta (título / título+resumen / completa) — **Fase P2,
la siguiente**; impresión de tableros (P3). El autocompletado y los chips
con color YA existen (P1). Detalles: `roadmap-and-decisions.md` y `PLAN.md`.
