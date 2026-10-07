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
> **Estado tras la Sesión 2 (2026-09-29):** P0 corregida, i18n reducido a
> EN/ES/DE/FR, P1.5 selección múltiple + acciones bulk, plantilla Kanban
> Estándar al crear tableros, nombre del tablero visible en toolbar y
> ventana. 100/100 tests OK. Pendiente de prueba manual por el usuario;
> próximo hito grande: Fase P1 (chips con color + autocompletado).

## Guía de lectura

| Archivo | Qué contiene | Cuándo leerlo |
|---|---|---|
| [`project-overview.md`](project-overview.md) | Qué es la app, stack, cómo compilar/correr/probar, estructura de carpetas, launchers portables, datos y configuración | Al inicio de cualquier sesión |
| [`architecture.md`](architecture.md) | Arquitectura hexagonal capa por capa, mapa de clases clave con rutas, patrones, reglas de ArchUnit, esquema SQLite completo, flujo undo/redo, sistemas ya implementados (drag&drop, i18n, temas, markdown, colores) | Antes de tocar código: para saber DÓNDE va cada cambio |
| [`roadmap-and-decisions.md`](roadmap-and-decisions.md) | Requisitos del usuario pendientes, hallazgos verificados (incluida la causa raíz del bug de guardado), decisiones de diseño con su porqué, preferencias del usuario, convenciones de trabajo en sesiones | Siempre: al planificar y antes de implementar |
| [`manual-usuario.md`](manual-usuario.md) | Manual de usuario completo: qué es, cómo ejecutarla, anatomía de la ventana, tableros/columnas/tarjetas, detalle, lote, filtros, procesos, precedencias, tiempo, apariencia, atajos, datos/portabilidad, export/import, sync online y solución de problemas | Para entender la app como usuario o al documentar cambios visibles |
| [`mejoras-propuestas.md`](mejoras-propuestas.md) | Mejoras propuestas (Sesión 9), priorizadas y **sin implementar**, con checkboxes para revisarlas durante las pruebas | Al probar el programa, para decidir el siguiente hito |
| [`plan-sesiones.md`](plan-sesiones.md) | Plan por sesiones para implementar las mejoras recomendadas: fases, criterios de avance, checklist y condición de reanudación | Para saber qué se va a hacer, en qué orden, y por dónde quedó si se interrumpe |
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
