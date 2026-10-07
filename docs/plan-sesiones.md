# Plan por sesiones — mejoras adicionales de Personal Kanban

> Estado: **plan activo; código de la Fase A hecho en esta sesión; pendiente compilar y probar en tu entorno**.
> Este documento es la cinta de progreso del trabajo: explica qué se va a hacer,
> en qué orden, con qué criterio, y sirve para reanudar si la sesión se
> interrumpe.

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

Objetivo: que sea posible movirse por las tarjetas del tablero con el teclado
(sin depender solo del ratón), editar la tarjeta con foco y salir del modo de
edición, sin que la app se olvide de dónde está el foco.

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
| Fase A — Navegación por teclado | sí | sí | no (código + i18n hechos; compile/test/manual pendientes) | sí | navegación por teclado, diálogo de atajos habilitado desde Ayuda, claves completas; pendiente compilar, ejecutar tests (incl. I18nCoverageTest) y probar manualmente |
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
