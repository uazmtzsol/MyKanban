# Progreso: visor de copias de conflicto (3.1 de adiciones-propuestas.md)

> Nota interna de sesión. **Estado: COMPLETADO** (Sesión 12).

## Qué quedó implementado

- **Lectura pura** (`application/CurrentConflicts.java`): lista los archivos
  `<bdDir>/conflicts/<boardId>-<epoch>.json` de un tablero, del más reciente al
  más antiguo (`listFor(BoardId)`), y abre una copia con su manifiesto de campos
  en conflicto (`review(Path)`). Sin dependencias de infrastructure: el DTO
  `ConflictCopy` vive en `application.sync` (regla ArchUnit cumplida).
- **UI** (`ui/ConflictReviewDialog.java`): diálogo con la lista de copias a la
  izquierda y, al seleccionar, el detalle tablero · fecha · nº de conflictos y la
  lista entidad/campo con valor local → valor remoto a la derecha.
- **Integración en el menú**: **Tableros → Ver copias de conflicto…**
  (`BoardController.onShowConflicts`), que abre el diálogo para el tablero activo.
  Una lista vacía es el estado válido "nunca se perdió nada".
- **i18n**: claves `view.conflicts.*` / `view.conflict.*` en los 5 bundles,
  vigiladas por `I18nCoverageTest`.
- **Tests**: `CurrentConflictsTest` (listado + revisión con datos reales en disco)
  y `AppContextGlobalLabelsTest`-estilo round-trip ya cubierto por la suite.

## Decisiones

- Diseño separado: helper puro (`CurrentConflicts`) + UI, sin tocar `SyncService`.
- Filtro por nombre de archivo (`<boardId>-*.json`) en vez de escaneo completo.
- `ConflictReviewDialog` recibe el directorio de datos y construye su propio
  lector (antes creaba `new CurrentConflicts(null)` y fallaba con NPE al abrir el
  detalle: corregido).
- Falta futura ( fuera de alcance de 3.1): **restaurar** una copia de conflicto
  como tablero/versión; hoy el visor es de solo lectura.

## Cierre

- Compilación y suite en verde (378 tests, 1 skip: endpoint PHP sin servidor).
- Commits: `feat(sync): review conflict copies from the Boards menu` (ver git).
