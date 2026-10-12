# 📚 Documentación interna — Personal Kanban (Java)

Carpeta de conocimiento persistente del proyecto. **Objetivo:** que cualquier
sesión futura (humana o agente) lea aquí el contexto completo en minutos, sin
volver a explorar todo el código.

> **Regla de mantenimiento:** tan pronto como surja en una sesión una
> decisión, un hallazgo o una preferencia del usuario que no deba perderse,
> se actualiza esta documentación ANTES de cerrar la sesión. El plan de
> trabajo vivo está en `PLAN.md` (raíz del repo); el resto del contexto
> estable vive aquí.
>
> **Estado tras la Sesión 13 (2026-10-12):** sesiones 5–13 entregadas con
> suite verde (**395/395**, 1 skip: endpoint PHP sin servidor) y jar portable
> regenerado; sigue pendiente la prueba manual del usuario. De la Sesión 11
> del plan queda **abierto S11-6** (excepción al personalizar atajos de
> teclado). El estado vivo está en [`PLAN.md`](../PLAN.md) y el listado
> verificado hecho / pendiente de cada mejora propuesta, en
> [`pending-mejoras-propuestas.md`](pending-mejoras-propuestas.md).

> **Sesión 14 en curso (2026-10-12):** el usuario pidió las siete mejoras de
> producto B1–B5 / B7 / B8 en bloque (informe de tiempo, búsqueda global,
> recordatorios de vencimiento, captura rápida con sintaxis, plantillas
> propias, registro de etiquetas con color y subtareas jerárquicas). Plan
> detallado: [`plan-sesion-14.md`](plan-sesion-14.md), con orden, alcance y
> criterio de hecho por mejora; cada una se cierra con documentación, tests y
> commit + push propios.

## Guía de lectura

| Archivo | Qué contiene | Cuándo leerlo |
|---|---|---|
| [`project-overview.md`](project-overview.md) | Qué es la app, stack, cómo compilar/correr/probar, estructura de carpetas, launchers portables, datos y configuración | Al inicio de cualquier sesión |
| [`architecture.md`](architecture.md) | Arquitectura hexagonal capa por capa, mapa de clases clave con rutas, patrones, reglas de ArchUnit, esquema SQLite completo, flujo undo/redo, sistemas ya implementados (drag&drop, i18n, temas, markdown, colores) | Antes de tocar código: para saber DÓNDE va cada cambio |
| [`roadmap-and-decisions.md`](roadmap-and-decisions.md) | Requisitos del usuario pendientes, hallazgos verificados (incluida la causa raíz del bug de guardado), decisiones de diseño con su porqué, preferencias del usuario, convenciones de trabajo en sesiones | Siempre: al planificar y antes de implementar |
| [`manual-usuario.md`](manual-usuario.md) | Manual de usuario completo: qué es, cómo ejecutarla, anatomía de la ventana, tableros/columnas/tarjetas, detalle, lote, filtros, procesos, precedencias, tiempo, apariencia, atajos, datos/portabilidad, export/import, sync online y solución de problemas | Para entender la app como usuario o al documentar cambios visibles |
| [`mejoras-propuestas.md`](mejoras-propuestas.md) | Mejoras propuestas (Sesión 9), priorizadas y **sin implementar**, con checkboxes para revisarlas durante las pruebas | Al probar el programa, para decidir el siguiente hito |
| [`import-merge-tableros.md`](import-merge-tableros.md) | **Propuesta pendiente de conversación**: al importar un tablero que ya existe, fusionarlo en vez de duplicarlo. Qué hace hoy el código, cómo lo resuelven otros programas, diseño propuesto y preguntas abiertas a decidir | Antes de implementar cualquier merge de importación; para discutir cómo debe funcionar |
| [`plan-sesiones.md`](plan-sesiones.md) | Plan por sesiones para implementar las mejoras recomendadas: fases, criterios de avance, checklist y condición de reanudación | Para saber qué se va a hacer, en qué orden, y por dónde quedó si se interrumpe |
| [`plan-sesion-14.md`](plan-sesion-14.md) | **Plan de la Sesión 14** (en curso): las siete mejoras de producto pedidas en bloque (B1 informe de tiempo, B2 búsqueda global, B3 recordatorios de vencimiento, B4 captura rápida, B5 plantillas + purga, B7 registro de etiquetas con color, B8 subtareas) con alcance, diseño por capas, archivos, claves i18n, tests y criterio de hecho de cada una | Al retomar la Sesión 14 o al revisar por qué cada mejora se diseñó así |
| [`pending-mejoras-propuestas.md`](pending-mejoras-propuestas.md) | **Listado actualizado (2026-10-12) del estado de cada mejora propuesta** (fuentes: `docs/mejoras-propuestas.md`, `docs/plan-sesiones.md`, `docs/roadmap-and-decisions.md`, y verificación directa en el código): hecho / sin implementar / pendiente de conversación × 3, con qué decisiones de diseño propone y qué encaje tiene | Para elegir la siguiente mejora sin volver a leer todos los documentos, antes de cualquier sesión de implementación |
| [`adiciones-propuestas.md`](adiciones-propuestas.md) | Mejoras adicionales recomendadas (nueva sesión, **sin implementar**), priorizadas y con criterio de cuándo valen, para decidir qué entra después | Para elegir la siguiente mejora desde una visión más ampla |
| [`PLAN.md`](../PLAN.md) — *(raíz del repo)* | Roadmap por fases P0–P3 con estado de avance, checklist de pruebas manuales por fase | Al inicio de cada sesión para saber en qué fase vamos |

## Convenciones de esta documentación

- Se escribe en **español** (idioma de trabajo con el usuario). El código,
  comentarios y mensajes de commit en el repo están en inglés — así se sigue.
- Cada entrada de decisión lleva fecha y contexto. Las decisiones pueden
  revertirse, pero se documenta el porqué de lo vigente.
- Los hallazgos técnicos ("descubrimientos" sobre el código o librerías) se
  marcan con ✔️ cuando fueron **verificados empíricamente** (test, jshell,
  build) y no solo deducidos de leer código.
- Los requisitos del usuario se citan con sus palabras cuando el matiz importa.
