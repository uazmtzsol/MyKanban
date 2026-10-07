# Mejoras adicionales recomendadas para Personal Kanban

> **Propósito:** lista de mejoras que conviene considerar, anclada al estado real del proyecto
> (código, i18n, tests, sesiones previas) y organizada para que el usuario pueda ir marcando
> lo que quiere probar. Ninguna está implementada aquí; es decisión del usuario el orden.

## Referencia de contexto usado para estas recomendaciones

- **Stack:** Java 21, JavaFX 21.0.12, SQLite 3.53.4.0, Jackson 2.18.2, commonmark-java 0.24.0,
  PDFBox 3.0.3, Maven 3.9.11.
- **Tests:** 236 corridos, 0 fallos, 1 skipped (PhpSyncApiEndpointTest, salta si no hay servidor PHP);
  JaCoCo imprime un error ambiental con JDK 25 (~«Unsupported class file major version 69») pero
  **los tests corren igual**; no es un fallo de la tarea.
- **Arquitectura:** hexagonal con ArchUnit (domain puro, application sin javafx/JDBC/ui,
  ui sin JDBC/infrastructure). Cualquier mejora nueva debe respetar eso.
- **i18n:** EN/ES/DE/FR, con `I18nCoverageTest` que exige que toda clave usada en `ui` exista en
  los 4 bundles. Toda mejora visible nueva añade claves a los 4 archivos.
- **Sesiones ya entregadas (resumen):** P0 corregido, i18n reducido, selección múltiple + bulk,
  plantillas de tablero, atajos fijos + F1, fondo personalizable, notas/checklist/process/precedencias,
  modos de vista de tarjeta (Ctrl+1/2/3 y override individual), Sort por prioridad, soltar arriba,
  persistencia visible (checkpoint cada 5 min + cierre limpio + Ctrl+S), diálogo de preferencias,
  **sync §4/§4.1** (merge 3-vías, servicio, copias de conflicto, botón Sincronizar ahora) y
  **B6 (clic en chip de etiqueta = filtrar)** ya implementado en la rama actual.
- **Pendiente en el roadmap:** outbox/fase 1 de sync, deltas en vez de snapshot, sync automático/backoff,
  lápidas, visor de copias de conflicto, fractional index para orden, impresión (P3), registro de etiquetas (P3).

---

## 1. Mejoras de UX directamente sobre lo que ya existe (alto valor, bajo riesgo)

### 1.1 Atajo de teclado para filtrar por proceso (proxy de T1)
Hoy el filtro de proceso vive en la barra y en el menú Procesos, pero no tiene atajo rápido como el
filtro de etiquetas (Ctrl+F enfoca el campo de etiquetas). Sugerir un `Ctrl+Shift+P` que enfoca/elige
el combo de proceso, consistente con lo que ya hay en `BoardController.bindScene`.

### 1.2 Foco de teclado y navegación entre tarjetas en modo kanban
La app es usable por ratón; falta un modo de navegación por teclado (flechas entre tarjetas,
Enter para editar, Esc para salir). Esto es especialmente útil cuando el tablero tiene muchas tarjetas
y el usuario quiere trabajar sin cambiar continuamente de mano al ratón. No requiere arquitectura nueva;
es UI + comandos ya existentes.

### 1.3 “Vista de proceso” como vista real del tablero, no solo menú
Hoy hay `processView` en el controlador, pero el flujo de uso no está documentado como vista de uso
diario. Si el usuario quiere trabajar por proceso, conviene que esa vista sea tan accesible como la de
columnas (toolbar + atajo + estado visible). Queda por pulir qué significa eso en pantalla.

### 1.4 Resaltar y navegar por precedencias desde la tarjeta
Ya hay badges `←n →n` y el menú de columna ofrece orden sugerido. Un paso natural es: desde una tarjeta,
resaltar temporalmente sus previas/posters o acceder a ellas rápido (atajo o menú contextual). Esto
endurece el valor del modelo de precedencias que ya existe.

### 1.5 Resumen de tarjeta configurable (líneas del frente)
El resumen del frente usa `MarkdownSummary.render(md, maxLines)` con 3 líneas en modo preview y
Integer.MAX_VALUE en modo completo. Una mejora pequeña y barata es dejar que el usuario elija cuántas
líneas muestra el resumen (p. ej. 2/3/5) sin tocar el modo completo. Esto entra en `CardViewSettings`
(sin migración, clave `app_setting`).

### 1.6 Filtro guardado por tablero (filtros frecuentes)
Hoy el filtro se aplica en caliente pero no se guarda como preferencia. Si el usuario tiene un tablero
donde habitualmente filtra por una etiqueta/ proceso, le conjugation guardar ese filtro como predeterminado
del tablero (o varios filtros rápidos). Esto se puede hacer sin migración usando `app_setting` por tablero.

---

## 2. Mejoras de “trabajo diario” que aprovechan el modelo ya existente

### 2.1 Recordatorios de fecha de vencimiento
Ya hay `dueDate`, badge de vencida y `Card.isOverdueOn(...)`. Lo que falta es hacer que ese dato
se notifique antes de que pase: un mecanismo sencillo de “tarjetas que vencen hoy / esta semana” y,
si se quiere, una notificación del sistema. No hay que inventar un motor de recordatorios complejo; puede
empezar como una vista/filtro derivado del campo que ya existe.

### 2.2 Informe/resumen de tiempo (si hay uso de tiempo)
Si la app lleva tiempo por tarjeta o por checklist, el siguiente paso natural es explotarlo: visión de
“qué estoy llevando”, estado de avance, y, si se quiere, export simple (p. ej. CSV o un JSON de resumen).
No hay que suponer que el usuario lleva tiempo hoy; es una mejora a evaluar según si el seguimiento de
tiempo empieza a usarse.

### 2.3 Búsqueda en el tablero activo (buscar tarjeta por texto)
No existe búsqueda global. Una mejora de uso inmediato es la búsqueda dentro del tablero activo: título,
etiquetas, descripción, notas, checklist. Puede empezar como una búsqueda local en el tablero actual
(Constructor + `BoardService` ya tienen el acceso). El nivel global entre tableros es un paso posterior.

### 2.4 Plantillas de tablero guardadas por el usuario
Hoy hay plantillas fijas en la creación. Si el usuario tiene tableros recurrentes, lo natural es poder
guardar un tablero como plantilla y reutilizarlo. Esto se puede empezar sin cambiar el dominio, como
una copia estructurada (columnas + plantilla de procesos, sin datos de tarjetas) guardada como preferencia
o como tablero especial. Es más complejo que las mejoras de la sección 1 porque toca la semántica de
tablero.

---

## 3. Sync y colaboración (cerrar lo ya empezado)

### 3.1 Visor de copias de conflicto
La rama actual ya escribe la copia de conflicto por disco (`<bdDir>/conflicts/...`) cuando hay conflicto,
pero la app no la muestra ni permite revisarla. Cerrar §4.1 del diseño implica:
- listar las copias existentes,
- ver qué fields entraron en conflicto,
- restablecer/versiones desde esa copia si el usuario quiere.

### 3.2 Desempate determinista de conflictos con identidad de dispositivo
El protocolo actual resuelve empates con una comparación canónica (mayor valor), que es reproducible pero
no “el dispositivo que más recientemente concretó”. Para el caso de uso personal de varias máquinas,
conviene añadir `device_id` estable en `sync_meta` y usarlo como desempate documentado. Esto prepara la
Fase 1 de outbox.

### 3.3 Orden estable (fractional index / enteros dispersos)
Hoy el orden es `position` entero reescrito al guardar. Al sincronizar, eso genera falsos conflictos de
orden y, en general, un objeto de orden que se reescribe en bloque. Una clave de orden dispersa permite
insertar en medio sin renumerar y reduce conflictos. Queda como deuda futura documentada en el diseño de
sync.

### 3.4 Sync automático en segundo plano + backoff + detección de offline
Hoy hay “Sincronizar ahora”. El siguiente nivel es sync automático (con reintentos y backoff, y con
detección de cuándo hay red). Es lo que hace que el sync deje de ser un botón y pase a ser una app que
“está sincronizada”. Queda como Fase 4 del diseño.

---

## 4. Robustez operativa (para el usuario que usa la app con datos reales)

### 4.1 Backup de base de datos automático
Un `.db` es autocontenido, pero si se corrompe o se sobreescribe, no hay red de seguridad. Una rotación
simple de backups (copias con timestamp, N copias) + verificación de integridad (`PRAGMA integrity_check`,
cuando corresponda) da tranquilidad para uso real. El usuario podría activar/desactivar esto en
preferencias.

### 4.2 Logging persistente básico
Hoy, cuando algo falla, la app muestra un diálogo, pero no queda rastro persistente del fallo. Un log
simple (rotativo, no invasivo) ayuda a diagnosticar problemas reales entre sesiones y a distingir fallos
duplicados de problemas nuevos.

### 4.3 Empaquetado nativo (jpackage) y auto-actualización
El proyecto ya puede empaquetar un fat jar portable; el siguiente nivel es un instalador nativo
(.msi/.deb/.dmg) y un mecanismo de actualización. Es una mejora de distribución y experiencia, no de
funcionalidad, pero vale mucho para que el usuario no dependa de Maven ni de la terminal.

### 4.4 Cierres y transporte de la BD más claros
Ya hay persistencia continua, checkpoint y cierre limpio. Una mejora de UX operativa es comunicar mejor
cuándo la BD está “segura de mover” (sin `.wal`/`.shm` huérfanos, sin writer activo) y ofrecer un modo
explícito de “cerrar base para copiarla”. Ayuda al usuario que transporta la BD entre computadoras.

---

## 5. Ingeniería / calidad interna (para que el proyecto aguante más sesiones sin erosionarse)

### 5.1 Probar `SyncMerge` con propiedades, no solo ejemplos
El motor de merge 3-vías es el código más sutil del repo. Los tests de ejemplo cubren casos diseñados;
las propiedades (conmutatividad, determinismo, que un cambio unilateral no se pierda, que la salida sea
siempre un estado válido) cazan clases de bugs que los ejemplos no ven. Los tests de propiedades se pueden
hacer sin dependencias nuevas (generador controlado + semilla fija).

### 5.2 JaCoCo / CI
JaCoCo 0.8.12 no instrumenta con JDK 25 (error ambiental documentado). Para que la cobertura y la CI
corran limpios, conviene subir el plugin de cobertura y tener un workflow de GitHub Actions que ejecute
`mvn test` en push/PR. Hoy el CI existe en `.github` pero es cuestión de verificar que corre sin JavaFX
en el runner.

### 5.3 Cobertura del filtro de proceso y de los atajos opcionales
El filtro de proceso, el modo de vista de tarjeta y los atajos ya existen, pero hay que asegurar que
todos los flujos visibles tengan clave i18n y que los nuevos trails de UX no se abran sin las claves
(nadie quiere ver claves crudas como en la sesión P0). `I18nCoverageTest` ya lo vigila; hay que mantenerlo
presente en cada mejora.

---

## Top recomendaciones (si hay que empezar por algo)

1. **Navegación por teclado básica + atajo de proceso (1.2 + 1.1):** usa lo que ya hay, es barato, y
   cambia la sensación de uso diario.
2. **Búsqueda en tablero activo (2.3):** resuelve el caso “tengo muchas tarjetas y no encuentro la que
   quiero” sin arquitectura nueva.
3. **Visor de copias de conflicto (3.1):** cierra el sync que ya se implementó; sin esto el sync resuelve
   conflictos pero no los muestra, y el usuario no tiene forma de ver qué perdió.
4. **Backup simple (4.1):** para el usuario que tiene datos reales, esto es tranquilidad barata.

---

## Qué NO recomendar ahora

- Añadir dependencias nuevas o migraciones de esquema a ciegas: el proyecto tiene una política clara de
  no añadir migraciones en P0–P2 salvo necesidad real (ver roadmap y decisiones).
- Reescribir el motor de sync antes de tener `device_id` y outbox: la política de conflictos está hecha,
  pero el mecanismo de delta todavía no lo está.
- Impresión (P3): está explícitamente desplazada por petición del usuario.

---

## Cómo usar esta lista

- El usuario puede marcar uno o varios usando **qué le falta hoy** como criterio, no el orden del documento.
- Cada mejora se puede convertir en sesión pequeña (implementar + tests + i18n en los 4 bundles +
  actualizar `PLAN.md` y los checks de prueba manual) respetando la regla de arquitectura hexagonal.
- Las mejoras de la sección 1 suelen ser las más rápidas de validar manualmente; las de sync (3.x) y
  robustez (4.x) son más largas y van mejor como sesiones separadas.
