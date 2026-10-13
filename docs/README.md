# 📚 Documentación interna — Personal Kanban (Java)

Carpeta de conocimiento persistente del proyecto. **Objetivo:** que cualquier
sesión futura (humana o agente) lea aquí el contexto completo en minutos, sin
volver a explorar todo el código.

> **⭐ Documento único de trabajo: [`../PLAN.md`](../PLAN.md).** Contiene el
> **plan**, el **checklist** de todo lo pendiente, el **estado** (hecho / en
> curso / pendiente) y la **bitácora** de avance con el commit de cada tarea.
> Si solo se va a leer un archivo para saber qué falta o por dónde se quedó, es
> ese. Se actualiza en cada tarea, antes del commit.

> **Regla de mantenimiento:** tan pronto como surja en una sesión una decisión,
> un hallazgo o una preferencia del usuario que no deba perderse, se actualiza
> esta documentación ANTES de cerrar la sesión. El plan y la bitácora viven en
> `PLAN.md` (raíz del repo); el contexto estable vive aquí.

> **Regla de cierre (permanente, pedida por el usuario):** siempre que se
> termine algo importante — una **característica nueva**, el **fix de un
> problema** o un avance relevante — hay que ① **documentar el avance y los
> hallazgos** (bitácora `PLAN.md` §3, checklist §1 y este `manual-usuario.md` si
> se ve en la UI), ② hacer **commit** con un mensaje que explique el porqué y
> ③ **push**. Nunca se deja trabajo terminado sin documentar ni sin subir.

> **Estado (2026-10-13):** rama `ImprovementsV1`; suite **512/512 OK** (1 skip:
> endpoint PHP sin servidor); migraciones hasta **V10**. Documentación
> consolidada en un solo documento: los antiguos `plan-sesiones.md`,
> `plan-sesion-14.md`, `pending-mejoras-propuestas.md`, `mejoras-propuestas.md`,
> `adiciones-propuestas.md`, `design.md` y las notas de sesión se retiraron
> (su contenido está en `PLAN.md` o en el historial de git).

## Guía de lectura

| Archivo | Qué contiene | Cuándo leerlo |
|---|---|---|
| [`../PLAN.md`](../PLAN.md) — *(raíz del repo)* | **Plan, checklist, estado y bitácora**: todo lo pendiente con su tarea (T-n), prioridad, alcance, tests y criterio de hecho; lo ya entregado; los bugs abiertos; las decisiones abiertas; la prueba manual pendiente | **Al empezar cualquier sesión** |
| [`project-overview.md`](project-overview.md) | Qué es la app, stack, cómo compilar/correr/probar, estructura de carpetas, launchers portables, datos y configuración | Al inicio de cualquier sesión |
| [`architecture.md`](architecture.md) | Arquitectura hexagonal capa por capa, mapa de clases clave con rutas, patrones, reglas de ArchUnit, esquema SQLite completo, flujo undo/redo, sistemas ya implementados (drag&drop, i18n, temas, markdown, colores) | Antes de tocar código: para saber DÓNDE va cada cambio |
| [`roadmap-and-decisions.md`](roadmap-and-decisions.md) | Requisitos históricos del usuario, hallazgos verificados y decisiones de diseño con su porqué, preferencias y convenciones de trabajo | Al planificar y antes de implementar |
| [`manual-usuario.md`](manual-usuario.md) | Manual de usuario completo: ventana, tableros/columnas/tarjetas, detalle, lote, filtros, procesos, precedencias, tiempo, apariencia, atajos, datos/portabilidad, export/import, sync y solución de problemas | Para entender la app como usuario o al documentar cambios visibles |
| [`sync-design.md`](sync-design.md) | Diseño de la sincronización: modelo de outbox y lápidas, política de conflictos por campo, comparativa de backends gratuitos y plan por fases | Antes de tocar sync o retomar sus fases 1–5 |
| [`import-merge-tableros.md`](import-merge-tableros.md) | **Propuesta pendiente de conversación** (C-1): al importar un tablero que ya existe, fusionarlo en vez de duplicarlo. Qué hace hoy el código, cómo lo resuelven otros programas, diseño propuesto y preguntas abiertas | Antes de implementar cualquier merge de importación |

## Convenciones de esta documentación

- Se escribe en **español** (idioma de trabajo con el usuario). El código,
  comentarios y mensajes de commit en el repo están en inglés — así se sigue.
- Cada entrada de decisión lleva fecha y contexto. Las decisiones pueden
  revertirse, pero se documenta el porqué de lo vigente.
- Los hallazgos técnicos ("descubrimientos" sobre el código o librerías) se
  marcan con ✔️ cuando fueron **verificados empíricamente** (test, jshell,
  build) y no solo deducidos de leer código.
- Los requisitos del usuario se citan con sus palabras cuando el matiz importa.
