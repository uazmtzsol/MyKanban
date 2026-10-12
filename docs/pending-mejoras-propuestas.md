# ✅ Estado de cada mejora propuesta — Personal Kanban

> **Qué es:** el listado que promete [`README.md`](README.md): cada mejora
> propuesta en los documentos de planificación, con su estado **verificado
> contra el código** (no copiado del plan).
>
> **Actualizado:** 2026-10-12 (sesión 14, B1 hecho). Fuentes cruzadas:
> [`mejoras-propuestas.md`](mejoras-propuestas.md) (A/B/C/D),
> [`adiciones-propuestas.md`](adiciones-propuestas.md) (1–5),
> [`plan-sesiones.md`](plan-sesiones.md) (S11-1…S11-6),
> [`roadmap-and-decisions.md`](roadmap-and-decisions.md) (ideas en reserva) y
> `PLAN.md` (historial por sesión, que es la fuente de las fechas y commits).
>
> **Cómo leerlo:** ✅ hecho · ⬜ sin empezar · 💬 pendiente de conversación ·
> 🐞 bug abierto. El número entre parémetros es el identificador original de su
> documento, para poder saltar a la propuesta completa.

---

## 1. Cerrado y verificado

| Mejora | Id. | Cómo se cerró |
|---|---|---|
| Guardado de color (P0) | §2.1 | `BoardColor.stored()` + rollback en memoria (sesión 2) |
| Botones del editor con nombre de propiedad | §2.2 | Claves `dialog.ok/cancel` + `I18nCoverageTest` (sesión 2) |
| i18n reducido a EN/ES/DE/FR | §5 | Bundles recortados; `I18nCoverageTest` lo enforcea |
| Selección múltiple + acciones en lote | §3.10 | Botón ☐ por columna + barra de lote (sesión 2b) |
| Modos de vista de tarjeta (Ctrl+1/2/3 + override por tarjeta) | 3.16 / P2 | `CardViewSettings` + menú ☳ (sesión 2g) |
| Chips de etiquetas con color determinista + autocompletado | P1 | `card-label-chip` + `LabelAutoComplete` (sesión 2f) |
| Ordenar columna por prioridad, soltar arriba, resize de columnas | — | Sesión 5 |
| Notas, checklist, procesos y precedencias | 4.x | Migraciones V5–V7 (sesión 4) |
| Seguimiento de tiempo por tarjeta | — | Migración V8 + pestaña Tiempo (sesión 6) |
| Sin `-wal`/`-shm` (diario de rollback) + cierre limpio | §3.11 | Sesión 7 (`DurabilityTest`) |
| Sync §4/§4.1: merge 3-vías, servicio, copias de conflicto, «Sincronizar ahora» | — | Sesión 9 |
| **Visor de copias de conflicto** | A1 / 3.1 | `ConflictReviewDialog` + `CurrentConflicts` (2026-10-11, `9606a64`) |
| Tests de propiedades del `SyncMerge` | D1 / 5.1 | `SyncMergePropertyTest` (9 propiedades, sesión 9) |
| CI en GitHub Actions | D3 / 5.2 (parte) | `.github/workflows/ci.yml` (`mvn -B test`) |
| Clic en el chip de etiqueta = filtrar | B6 | `LabelChipFilter` + tooltip (2026-10-05) |
| Atajo del filtro de proceso (Ctrl+Shift+P / Ctrl+Shift+N) | 1.1 | Aceleradores en el menú Procesos |
| Navegación por teclado entre tarjetas | 1.2 | `CardNavigator` + filtro de escena (sesión 10) |
| Presupuesto de líneas del resumen (preview ≠ extensa) | F2 | `LineBudget` (sesión 10) |
| Exportar tarjeta a txt/markdown/PDF seleccionable | F3 | `CardExporter` + `CardPdfWriter` (sesión 10) |
| Archivar + limpieza de adjuntos huérfanos | F4 | Etiqueta reservada + papelera de adjuntos (sesión 10) |
| Catálogo **global** de etiquetas con gestor | S11-4 | `LabelCatalog` + `LabelManagerDialog` (2026-10-11) |
| Atajo de etiquetas sobre selección o tarjeta enfocada | — | `GlobalShortcuts.Action.LABELS` (Ctrl+L, 2026-10-11) |
| Quitar la imagen de fondo | S11-1 | `BackgroundFiles` eliminado; Preferencias sin pestaña Fondo |
| Esquema de colores personalizable + contraste del tema obscuro | S11-2 | `ThemeColors` + `ThemeOverride` + Preferencias |
| Markdown dentro de una nota ya no crea notas nuevas | S11-3 | Separador `%%` con lectura del formato legado (2026-10-11) |
| **Clic en una tarjeta = tarjeta «actual»** | S11-5 | `onCardSelected` + `.card-current` (2026-10-12) |
| Vista de procesos accesible como una vista más (menú + atajo + estado visible) | 1.3 | Menú **Ver** con `Ctrl+Shift+1/2` y marca del modo activo (2026-10-12) |
| Descripción de tarjeta legible en tema obscuro | 5.1 del PLAN | Clase `text` en `MarkdownSummary` + regla CSS efectiva (2026-10-12) |
| Tooltips que mostraban claves de properties | §2.2 | `bulk.process` en los 5 bundles + guardián de claves indirectas (2026-10-12) |
| **Informe de tiempo por proceso y semana (CSV/PDF)** | B1 / 2.2 | `TimeReport` + `TimeReportWindow` en `Ver → Informe de tiempo…` (2026-10-12) |
| **Búsqueda global entre tableros** | B2 / 2.3 | `GlobalSearch` + `GlobalSearchDialog` con Ctrl+Shift+F, 🔍 y menú Ver (2026-10-12) |

---

## 2. Abierto

### 2.1 🐞 Bugs conocidos (no son mejoras: hacen daño hoy)

- **S11-6 — «Ayuda → Personalizar atajos de teclado» puede lanzar excepción.**
  `KeyboardShortcutsDialog` pasa nombres de enum (`NEXT_CARD`) a
  `GlobalShortcuts.Action.fromStorageNameOrThrow(...)`, que solo acepta nombres
  de almacenamiento (`next`): el diálogo y su botón «Restaurar» pueden reventar
  con `IllegalArgumentException`. Plan ya escrito en `docs/plan-sesiones.md`
  (tolerar ambos nombres + llevar el enum en la fila). **Sin hacer.**
- **Deuda conocida, sin tocar** (sesión 10): las tarjetas archivadas siguen
  visibles en la vista de procesos; el interruptor «mostrar archivadas» es de
  sesión (no persiste).
- **Ayuda F1 incompleta:** se añadieron los atajos del menú Ver, pero las teclas
  fijas de tarjeta (flechas, dígitos 1–9, `Alt+I/U`, `Supr`) siguen fuera de la
  ventana de ayuda.

### 2.2 Sincronización

| Mejora | Id. | Nota |
|---|---|---|
| `device_id` estable para el desempate | A2 / 3.2 | Requiere migración V10 + `sync_meta` |
| Fractional index para el orden | A3 / 3.3 | Deuda de diseño reconocida (§7 del diseño de sync) |
| Sync automático en segundo plano + backoff + offline | A4 / 3.4 | Hoy es un botón |
| Outbox / deltas en vez de snapshot completo | A5 | Fase 1 |
| Lápidas con retención | A6 | Depende de A5 |
| Cifrado del payload remoto | A7 | Baja prioridad |

> Criterio: 3.2/3.3/3.4 se decidieron juntas en el diseño y conviene no trocearlas.

### 2.3 Producto

| Mejora | Id. | Nota |
|---|---|---|
| Recordatorios de vencimiento + vistas «vence hoy/semana» | B3 / 2.1 | El badge de vencida ya existe |
| Creación rápida con sintaxis (`Título #etiqueta ! @fecha`) | B4 | Parseo puro, sin migración |
| Plantillas propias guardables | B5 / 2.4 | Hoy solo hay plantillas fijas |
| Registro de etiquetas (color elegido por etiqueta) | B7 | Reservado como P3 |
| Subtareas jerárquicas | B8 | Hoy el checklist es plano por decisión del usuario |
| Nombres de color traducidos | B9 | El tooltip del selector muestra el nombre interno (`blue`); sin claves i18n de color (detectado el 2026-10-12) |
| Resaltar/navegar precedencias desde una tarjeta | 1.4 | Los badges y el orden sugerido ya existen |
| Resumen del frente configurable (2/3/5 líneas) | 1.5 | Entra en `CardViewSettings`, sin migración |
| Filtro guardado por tablero | 1.6 | `app_setting` por tablero |
| Impresión de tableros | P3 | Desplazada por petición explícita del usuario |

### 2.4 Robustez y distribución

| Mejora | Id. | Nota |
|---|---|---|
| Backup automático rotativo de la BD | C1 / 4.1 | **El mayor riesgo abierto** para el usuario con datos reales |
| Verificación de integridad (`PRAGMA integrity_check`) y reparar | C2 / 4.1 | Complementa a C1 |
| Empaquetado nativo con `jpackage` + auto-actualización | C3 / 4.3 | Hoy es fat jar + `.bat`/`.sh` |
| Logging persistente rotativo | C4 / 4.2 | Hoy solo hay diálogo de error |
| Cifrado en reposo local (SQLCipher) | C5 | Opcional |
| «Modo copiar la base» explícito (transporte entre equipos) | 4.4 | El cierre limpio y la ausencia de sidecars ya existen |

### 2.5 Ingeniería

| Mejora | Id. | Nota |
|---|---|---|
| Arreglar JaCoCo (0.8.13+ o JDK de build compatible) | D2 / 5.2 | No instrumenta con JDK 25; los tests corren igual |
| E2E del cliente HTTP contra el PHP | D4 / 5.1 | Hoy solo verificación manual |
| Prueba automática del foco inicial de los diálogos | D5 | El fix de la sesión 13 está verificado solo por revisión |
| Cobertura i18n de flujos nuevos | 5.3 | En marcha: `I18nCoverageTest` ahora caza claves indirectas |

### 2.6 Pendientes de conversación (no implementar sin decidir)

- **💬 Import con merge en vez de duplicar tablero** — propuesta completa y
  preguntas abiertas en [`import-merge-tableros.md`](import-merge-tableros.md).
- **💬 Impresión de tableros** — evaluar export HTML frente a `PrinterJob`
  (P3, desplazada por el usuario).

### 2.7 Prueba manual del usuario (siempre pendiente por diseño)

Cada sesión entrega código con suite verde y una checklist de prueba manual;
las checklists viven en `PLAN.md` (sesiones 5–13) y varias siguen sin marcarse
por el usuario. Antes de cerrar una sesión, recorrer la checklist de esa sesión.

---

## 3. Reglas que se mantienen al implementar cualquiera de estas

1. Arquitectura hexagonal (reglas de `ArchitectureTest`: dominio puro;
   `application` sin JavaFX/JDBC/UI; `ui` sin JDBC/infraestructura).
2. i18n en los **5** bundles (base + EN/ES/DE/FR): lo exige `I18nCoverageTest`,
   que también revisa las claves pasadas como argumento.
3. Cada mejora: código + test que falle sin el cambio + `PLAN.md` actualizado
   (estado y checklist manual) y commit **solo si el usuario lo pide**.
4. Nada de migraciones de esquema sin necesidad real (P0–P2 se resolvieron sin
   ellas); las que hagan falta van acompañadas de su feature.
