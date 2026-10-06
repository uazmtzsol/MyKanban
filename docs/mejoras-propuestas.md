# 💡 Mejoras propuestas — para revisar durante las pruebas

> **Estado (2026-10-05):** implementadas **B6**, **D1** y **D3** (ver más
> abajo y `PLAN.md` Sesión 9). El resto sigue **sin implementar**. Este
> documento existe para que no se pierdan las ideas al probar el programa.
> Marca la casilla `[ ]` → `[x]` de lo que te interese y pídeme que lo
> implemente en una sesión.
>
> Origen: propuestas de la **Sesión 9**, ancladas al estado real del código
> (no son ideas genéricas). Cada propuesta indica *por qué* y *dónde encaja*.
>
> Ordenadas por **valor / riesgo**: primero lo que cierra deuda de lo ya
> construido, luego producto nuevo, después robustez y por último ingeniería.
> Al final hay un **Top 3** recomendado.

## A. Cerrar el sync que ya existe (deuda inmediata)

| # | Mejora | Por qué | Dónde encaja | Prioridad |
|---|---|---|---|---|
| A1 | **Visor de copias de conflicto** | `JsonConflictCopyStore` ya escribe `<bdDir>/conflicts/<boardId>-<epoch>.json`, pero no hay forma de *verlas* ni restaurarlas desde la app. Es la última pieza de `docs/sync-design.md` §4.1 | `ui/` nuevo diálogo + puerto de lectura | 🔴 Alta |
| A2 | **`device_id` estable (Fase 1, V10)** | Hoy el desempate de conflictos es una comparación canónica trivial porque el protocolo no tiene identidad de dispositivo (`docs/sync-design.md` §4) | Migración V10 + `sync_meta` | 🟠 Media-alta |
| A3 | **Fractional index para el orden** | `position` entero se reescribe en cada `save`; al sincronizar produce falsos conflictos de orden (§7) | Dominio (`Board`/`BoardColumn`) + migración | 🟠 Media-alta |
| A4 | **Sync automático en segundo plano + reintentos con backoff + detección de offline** (Fase 4) | Hoy la sincronización es un botón manual (`Archivo → Sincronizar ahora`) | `application/sync` + `ui` | 🟠 Media |
| A5 | **Outbox / deltas (Fase 1)** en vez de snapshot completo | El MVP sube el tablero entero; los deltas permiten `GET/POST ?since=` y lápidas reales | `SyncRepository` + V10 | 🟡 Media |
| A6 | **Lápidas con retención** | Sin ellas, un borrado solo se detecta a nivel snapshot; con outbox hacen falta tombstones purgables (§Fase 4) | Persistencia + protocolo | 🟡 Media |
| A7 | **Cifrado del payload remoto** | Privacidad si algún día se usa un backend de terceros (§7) | `SyncRepository` (adaptador) | 🔵 Baja |

- [ ] A1 — Visor de copias de conflicto
- [ ] A2 — `device_id` estable
- [ ] A3 — Fractional index (clave de orden estable)
- [ ] A4 — Sync automático en segundo plano
- [ ] A5 — Outbox / deltas
- [ ] A6 — Lápidas con retención
- [ ] A7 — Cifrado del payload

## B. Producto — funciones nuevas sobre lo que ya hay

| # | Mejora | Por qué | Dónde encaja | Prioridad |
|---|---|---|---|---|
| B1 | **Informe/dashboard de tiempo** (horas por proceso, por semana; export CSV/PDF) | El seguimiento de tiempo por tarjeta ya existe (pestaña **Tiempo**); falta explotarlo. Es la mayor capacidad nueva con el menor código | `application` (consulta) + `ui` (ventana) | 🔴 Alta |
| B2 | **Búsqueda global** (Ctrl+Shift+F) sobre título, descripción, notas y checklist, cruzando tableros | Hoy no existe ninguna búsqueda fuera del filtro de etiquetas/proceso | `application` (caso de uso) + `ui` | 🟠 Media-alta |
| B3 | **Recordatorios de vencimiento** (notificación del sistema) + vistas "vence hoy / esta semana" y reagendado rápido | Ya hay badge de vencido, pero solo si miras el tablero | `ui` + `application` | 🟠 Media |
| B4 | **Creación rápida con sintaxis** en el campo de añadir (`Título #etiqueta ! @2026-10-10` → etiqueta, urgente, fecha) | Captura ideas sin abrir el diálogo | `ui` (parseo puro en `Dialogs`/intent) | 🟠 Media |
| B5 | **Plantillas propias** guardables (hoy solo las fijas Estándar / Personalizadas / Sin columnas) + **archivar/completar** tarjetas con purga | Reutilizar estructuras propias y evitar que "Hecho" crezca sin fin | `application` + persistencia de plantilla en setting | 🟡 Media |
| B6 | ✅ **Clic en el chip de etiqueta = filtrar por ella** (hecho) | Barato y muy útil (ya está en reserva en `roadmap-and-decisions.md` §4); clic de nuevo = quitar el filtro | `CardViewBuilder` + `BoardController` | ✅ Hecho |
| B7 | **Registro de etiquetas** (colores elegidos, renombrado propagado, autocompletado global) | Ya reservado como P3 | Migración propia | 🔵 Baja |
| B8 | **Subtareas jerárquicas** | Hoy el checklist es plano por decisión explícita | Dominio + UI | 🔵 Baja |

- [ ] B1 — Informe/dashboard de tiempo
- [ ] B2 — Búsqueda global
- [ ] B3 — Recordatorios de vencimiento
- [ ] B4 — Creación rápida con sintaxis
- [ ] B5 — Plantillas propias + archivado
- [x] B6 — Chip de etiqueta clicable = filtrar  ✅ 2026-10-05
- [ ] B7 — Registro de etiquetas
- [ ] B8 — Subtareas jerárquicas

## C. Robustez operativa y distribución

| # | Mejora | Por qué | Dónde encaja | Prioridad |
|---|---|---|---|---|
| C1 | **Backup automático rotativo** de `kanban.db` (N copias con fecha) + restauración | Hoy un `.db` corrupto no tiene red de seguridad; el JSON export es manual | `infrastructure` + `ui` | 🟠 Media-alta |
| C2 | **Verificación de integridad** (`PRAGMA integrity_check`) y "reparar base de datos" | Diagnóstico rápido si algo va mal | `infrastructure` | 🟡 Media |
| C3 | **Empaquetado nativo con `jpackage`** (`.msi`/`.deb`/`.dmg`) + auto-actualización | Hoy es fat jar + `.bat`/`.sh`; un instalador baja la fricción | `pom.xml` + scripts | 🟠 Media |
| C4 | **Logging persistente** (log rotativo) | No hay ni `java.util.logging`; al fallar solo queda el diálogo de error | `infrastructure` | 🟠 Media |
| C5 | **Cifrado en reposo local** (SQLCipher) opcional | Datos sensibles en un portátil/USB | `infrastructure` | 🔵 Baja |

- [ ] C1 — Backup automático rotativo
- [ ] C2 — Verificación de integridad
- [ ] C3 — Empaquetado nativo + auto-update
- [ ] C4 — Logging persistente
- [ ] C5 — Cifrado en reposo local

## D. Ingeniería (barato, alto retorno)

| # | Mejora | Por qué | Dónde encaja | Prioridad |
|---|---|---|---|---|
| D1 | ✅ **Tests de propiedades del `SyncMerge`** (hecho) | Es el código más sutil del repo (merge 3-vías). Propiedades verificadas: *determinismo*, *idempotencia* (nadie cambió → resultado idéntico), *invariancia* ("un cambio de un solo lado nunca se pierde"), *punto fijo*, *simetría del desempate* y *validez estructural* (nunca campos null). Sin librería nueva (offline): generador sembrado + bucles | `SyncMergePropertyTest` | ✅ Hecho |
| D2 | **Arreglar JaCoCo** (0.8.13+ o JDK de build compatible) | Hoy no instrumenta en JDK25 ("Unsupported class file major version 69"); los tests corren igual, pero sin cobertura | `pom.xml` | 🟡 Media |
| D3 | ✅ **CI con GitHub Actions** (`mvn test` en cada push/PR) | Hoy `.github/` no tiene workflow; la suite es rápida y estable | `.github/workflows/ci.yml` | ✅ Hecho |
| D4 | **Test E2E del cliente HTTP contra el PHP** | Ya hay verificación manual del adaptador; automatizarla evita regresiones de red | `src/test` (se marca como IT) | 🟡 Media |

- [x] D1 — Tests de propiedades del merge  ✅ 2026-10-05
- [ ] D2 — Arreglar JaCoCo
- [x] D3 — CI con GitHub Actions  ✅ 2026-10-05
- [ ] D4 — E2E del cliente HTTP

---

## ⭐ Top 3 si hay que elegir

1. **A1 — Visor de copias de conflicto**: cierra de verdad lo recién hecho (§4.1).
2. **D1 — Tests property-based del merge**: blinda el riesgo real antes de ampliar el sync.
3. **B1 — Informe de tiempo**: nuevo valor construido sobre algo que ya funciona.

## Notas de encaje con lo ya planificado

- **Ya planificado, no repetir aquí:** impresión/export HTML y registro de
  etiquetas (P3, `PLAN.md`), outbox/deltas y multi-dispositivo (Fases 1–5,
  `docs/sync-design.md`), escritura incremental en SQLite y clic-en-chip
  (`docs/roadmap-and-decisions.md` §4).
- **Restricciones a respetar al implementar cualquiera de estas:**
  arquitectura hexagonal (reglas de `ArchitectureTest`), i18n en los bundles
  (base + EN/ES/DE/FR; lo exige `I18nCoverageTest`) y tests verdes.
