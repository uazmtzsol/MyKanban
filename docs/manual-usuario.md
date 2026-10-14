# 📖 Manual de usuario — Personal Kanban

Guía de uso de la aplicación **Personal Kanban** (JavaFX · SQLite), una app de
escritorio para organizar trabajo personal con tableros kanban. Todo el dato
vive en un archivo SQLite local: nada sale de tu máquina salvo que tú actives
la sincronización online.

> Idioma de la interfaz: **EN / ES / DE / FR** (menú **Idioma**). Este manual
> usa los textos en español; si usas otro idioma, los nombres de menú son sus
> traducciones equivalentes.

---

## Índice

1. [Qué es y qué necesitas](#1-qué-es-y-qué-necesitas)
2. [Cómo ejecutarla](#2-cómo-ejecutarla)
3. [Primer arranque](#3-primer-arranque)
4. [La ventana principal](#4-la-ventana-principal)
5. [Tableros](#5-tableros)
6. [Columnas](#6-columnas)
7. [Tarjetas](#7-tarjetas)
8. [Detalle de tarjeta (pestañas)](#8-detalle-de-tarjeta-pestañas)
   - [8.1 Recordatorios (🔔)](#81-recordatorios-)
9. [Selección múltiple y acciones en lote](#9-selección-múltiple-y-acciones-en-lote)
10. [Filtros](#10-filtros)
11. [Procesos y vista de procesos](#11-procesos-y-vista-de-procesos)
12. [Precedencias (dependencias entre tarjetas)](#12-precedencias-dependencias-entre-tarjetas)
13. [Seguimiento de tiempo](#13-seguimiento-de-tiempo)
14. [Apariencia: tema y colores](#14-apariencia-tema-y-colores)
15. [Idioma](#15-idioma)
16. [Atajos de teclado](#16-atajos-de-teclado)
17. [Deshacer/Rehacer y guardado](#17-deshacerrehacer-y-guardado)
18. [Archivos, datos y portabilidad](#18-archivos-datos-y-portabilidad)
19. [Exportar e importar](#19-exportar-e-importar)
20. [Sincronización online (opcional)](#20-sincronización-online-opcional)
21. [Preguntas frecuentes](#21-preguntas-frecuentes)
22. [Solución de problemas](#22-solución-de-problemas)

---

## 1. Qué es y qué necesitas

- **Qué es:** un kanban para uso **personal**. Tableros, columnas con límite
  WIP, tarjetas con etiquetas, fechas límite, descripción markdown, notas,
  listas de tareas, procesos, precedencias y cronómetro, todo con deshacer.
- **Requisitos:** un **JDK/JRE 21+** para ejecutarla. Para compilar tú mismo,
  **Maven 3.9+**.
- **Dónde vive el dato:** un archivo `kanban.db` (SQLite) y una carpeta
  `history/` con el historial de deshacer. Ambos son la *unidad* del
  tablero/catálogo: si mueves esa carpeta, mueves todo (ver §18).

## 2. Cómo ejecutarla

**Desarrollo (desde el código):**

```bash
mvn javafx:run
```

**Versión portátil (jar autónomo, sin instalar JavaFX):**

```bash
mvn -Pportable package -DskipTests
```

Genera `target/personal-kanban.jar`. Para un USB o carpeta portátil, coloca
juntos:

```
personal-kanban.jar
kanban.bat        # Windows (doble clic)
kanban.sh         # Linux/macOS (./kanban.sh)
```

Los lanzadores:

- usan `jre/bin/java` **si existe a su lado** (opcional: mete un JRE 21 en una
  carpeta `jre/` para un USB 100 % autónomo); si no, usan el `java` del PATH.
- guardan **todos** los datos en `data/` junto al jar (vía `-Dpk.data.dir`),
  así que no escriben nada en la máquina anfitriona.

También puedes lanzarlo a mano:

```bash
java -Dpk.data.dir=./data -jar personal-kanban.jar
```

## 3. Primer arranque

Al abrir por primera vez, la app usa (o crea) una base de datos por defecto y
muestra un tablero. Puedes:

- **Trabajar con la base por defecto:** en `~/.personalkanban/`
  (`kanban.db` + `history/`).
- **Crear una base nueva** en la carpeta que quieras:
  **Archivo → Nueva base de datos...**
- **Abrir una base existente:** **Archivo → Abrir base de datos...**.

La elección se recuerda por equipo, así que en cada arranque se reabre tu
última base. Las bases usadas recientemente aparecen listadas en el menú
**Archivo** (las últimas 5) con un punto ● en la activa.

Bases de versiones antiguas (formato de un solo tablero) se **migran solas**:
columnas y tarjetas pasan al tablero **«My Board»**.

## 4. La ventana principal

De arriba abajo:

- **Barra de menús:** **Archivo**, **Tableros**, **Ver**,
  **Procesos**, **Etiquetas**, **Idioma**, **Ayuda**.
- **Barra de herramientas:**
  - 🔍 **Buscar en todos los tableros** (§10)
  - ⇄ **Cambiar entre kanban y procesos** (vista alternativa, §11)
  - ↶ **Deshacer** · ↷ **Rehacer**
  - 💾 **Guardar ahora** (todo se guarda tras cada cambio; es una
    confirmación, §17)
  - 🌙 **Modo oscuro**
  - 📄 **Exportar tablero a PDF**
  - ☷ **Modo de vista de tarjeta** (menú desplegable)
  - El **nombre del tablero activo** aparece junto a «Personal Kanban».
- **Barra de filtro:** filtrar por etiquetas, botones ★ y !, y filtro por
  proceso (§10).
- **Área de tablero:** las columnas con sus tarjetas, más una zona de aviso
  discreta (no modal) para mensajes de sincronización.

El **título de la ventana** muestra el nombre del archivo/base y un **sello de
versión** de la compilación (p. ej. `v2d-20261005-1830`), útil para saber qué
binario estás ejecutando.

## 5. Tableros

Menú **Tableros**:

- La lista de tableros (● el activo, ○ los demás) para cambiar con un clic.
- **Nuevo tablero...** — pide nombre y **plantilla de columnas**:
  - **Plantilla Kanban Estándar** (Por hacer · Haciendo · Hecho),
  - **Personalizadas** — un número (p. ej. `4`) o nombres separados por comas
    (p. ej. `Ideas, En curso, Revisión, Hecho`); máximo **12** columnas,
  - **Sin columnas**,
  - tus **plantillas propias** guardadas (§abajo).
- **Renombrar tablero...**
- **Eliminar tablero...** (con confirmación; no se puede eliminar el último).
- **Agregar columna...** — abre el mismo diálogo de columna y la añade al
  final del tablero.
- **Guardar como plantilla...** — guarda la **estructura** del tablero activo
  (columnas con su color y límite WIP, procesos y modo de vista) con un nombre.
  Las tarjetas **no** se guardan: al aplicar la plantilla el tablero nace vacío.
  Si ya existe una plantilla con ese nombre, se pide confirmación.
- **Plantillas** — submenú con tus plantillas; cada una ofrece **Aplicar
  (nuevo tablero)…** (pide el nombre del tablero nuevo) y **Eliminar
  plantilla** (los tableros ya creados no se tocan).
- **Borrar tarjetas archivadas...** — cuenta las tarjetas con la etiqueta
  interna de archivado en **todos** los tableros y, tras confirmar, las borra
  (sus archivos van a la papelera). El borrado se puede deshacer con
  **Ctrl+Z** en cada tablero afectado.
- **Exportar tablero (JSON)...**, **Exportar tablero a PDF...**,
  **Importar tablero (JSON)...** (§19).

## 6. Columnas

Cada columna tiene **cabecera** con: título (+ descripción en tooltip),
insignia de **WIP** (`n/límite`, en rojo cuando está llena), insignia de
**finalizada** (si la marcaste), y una fila de botones:

| Botón | Acción |
|---|---|
| ➕ | **Añadir tarjeta** a esta columna |
| ↓★ | **Ordenar por prioridad**: primero (★+!), luego (!), luego (★); el resto conserva su orden relativo |
| ☐ | **Selección de tarjetas** (modo lote, §9) |
| « | **Colapsar / expandir** la columna |
| ⋯ | **Menú de columna** (abajo) |

**Menú ⋯ de la columna:** **Añadir columna a la izquierda** · **Añadir columna
a la derecha** · — · Editar columna · Marcar como **finalizada**
(máximo una por tablero) · **Color de fondo...** · **Orden de la columna...**
(1 = primera) · Colapsar columna · Eliminar columna · — · **Eliminar todas
las tarjetas** de la columna. (El botón ➕ de **añadir columna** ya no está en
la barra de herramientas: la función vive aquí y en el menú **Tableros**,
junto a donde quieres la columna nueva.)

Al **editar** una columna puedes cambiar título, descripción, color y el
**límite WIP** (vacío = ilimitado). Si una columna está llena, la app impide
pasarse del límite al mover tarjetas.

**Reordenar columnas:** arrastra la cabecera de una columna a otra posición.
También puedes ajustar el **ancho** de las columnas arrastrando su borde.

## 7. Tarjetas

**Crear (captura rápida):** el botón ➕ de la columna abre un campo de una línea
con **vista previa en vivo**. Escribe el título y usa la sintaxis en línea:

| Escribe | Efecto |
|---|---|
| `+casa` | añade a la etiqueta «casa» (`+urgente` / `+importante` = las banderas ! / ★) |
| `!` | marca **urgente** |
| `*` | marca **importante** |
| `@hoy`, `@mañana`, `@+3d`, `@2026-10-20` | **fecha límite** (una fecha inválida se queda en el título) |

El resto del texto es el título (los signos dentro de una palabra no se tocan:
`C++`, `a*b`). Pulsa **Enter** (o **Añadir**) para crear la tarjeta; **Más
opciones…** abre el diálogo completo con lo ya escrito.

**Diálogo completo:** **Ctrl + ➕** sobre la columna o la entrada **«Añadir
tarjeta (diálogo completo)…»** del menú **⋯**. Tiene los campos básicos
(**Título**, **Descripción**, **Color**, **Fecha límite**, **Etiquetas**) y una
casilla **☑ Opciones avanzadas** que despliega: **Notas**, **Lista de tareas**
(una por línea; `[x] tarea` = hecha, `[ ] tarea` o texto suelto = pendiente),
**Proceso** y **Tareas previas / posteriores** (mantén **Ctrl** mientras
eliges).

**Frente de la tarjeta:** título, **contenido elegido** (descripción por
defecto; ver el botón de contenido, §abajo), **★/!** (clic = activar/desactivar
Importante/Urgente), **chips de etiquetas**, **fecha límite** (con aviso rojo si
ya venció), **chip de proceso** (⚬), progreso de lista **☑ hechas/total**,
**📝** si tiene notas, y contadores de precedencia **←n →n**. Al pasar el ratón
aparecen ✎ (editar) y ✕ (eliminar).

**Qué muestra el frente (por tarjeta):** un botón en la propia tarjeta elige el
contenido que aparece bajo el título: **① Descripción** (respetando el modo de
vista), **② Tareas** — la lista de tareas se puede marcar/desmarcar **desde el
propio frente**, en un solo paso de deshacer — o **③ Comentarios** (las
**notas** de la tarjeta). Es una elección por tarjeta y se recuerda al
reiniciar; por defecto todas muestran la descripción.

**Abrir el detalle:** doble clic sobre la tarjeta (§8).

**Clic derecho en una tarjeta:** elegir el **modo de vista de esa tarjeta**
(solo título / título + resumen / completa) o volver al **modo del tablero**.

**Modo de vista de todas las tarjetas:** botón **☷** de la barra de
herramientas (o **Ctrl+1 / Ctrl+2 / Ctrl+3**): ① solo título, ② título +
resumen (3 renglones), ③ título + texto completo.

**Mover tarjetas:** arrastra una tarjeta dentro de su columna o a otra. La
mitad **superior** de una tarjeta destino inserta **arriba** (línea azul
superior) y la inferior inserta **debajo**. También hay una ranura al final de
cada columna. Si la columna de destino tiene límite WIP, el movimiento se
bloquea si no cabe.

**Eliminar:** ✕ en la tarjeta (con confirmación).

## 8. Detalle de tarjeta (pestañas)

Doble clic en una tarjeta abre la ventana **Detalle de tarjeta**, con cinco
pestañas:

- **Descripción** — editor **markdown** con botón para alternar
  **Editar / Vista** (previa renderizada), **guía rápida de sintaxis** y
  **copiar todo** al portapapeles. Soporta encabezados, **negrita**,
  *cursiva*, listas, tablas, código y enlaces. La vista previa usa un WebView
  con JavaScript desactivado.
- **Notas** — texto **plano** (sin markdown), separado de la descripción.
  Pulsa **Guardar notas**. Aparece el 📝 en el frente con un extracto en el
  tooltip.
- **Lista de tareas** — añade tareas, márcalas/desmárcalas, renómbralas o
  elimínalas (clic derecho sobre un ítem). **Convertir en tarjeta** crea una
  tarjeta nueva en la **misma columna** con ese título y quita el ítem del
  checklist. Límite: 50 ítems por tarjeta, 120 caracteres por ítem.
  - **Subtareas (un nivel):** el menú de una tarea ofrece **Añadir subtarea**
    (se muestra **indentada** bajo su tarea) y **Sangrar / Quitar sangría** para
    convertir una tarea en subtarea o al revés. Borrar una tarea borra también
    sus subtareas, y el contador **☑ n/m** incluye las dos. En el formulario
    avanzado, **dos espacios** al principio de una línea la convierten en
    subtarea de la anterior. No hay más de un nivel (una subtarea no tiene
    subtareas).
- **Recordatorios** — fechas (con hora opcional) en las que la app te avisa de
  la tarjeta (§8.1).
- **Tiempo** — cronómetro por tarjeta (§13).

Cada pestaña guarda como una **transacción deshacible** (Ctrl+Z lo revierte).

### 8.1 Recordatorios (🔔)

Un **recordatorio** es un aviso que tú programas en **una tarjeta con fecha
límite**: en el momento elegido la app te muestra un mensaje con **el título de
la tarjeta** («recuérdame esta actividad»). Se pueden poner **tantos como
quieras** (hasta 200 por tarjeta), y cada uno es un *momento* suelto, no una
regla: eliges los días que quieras y después puedes borrar uno solo.

**Cómo se ponen**

1. Abre la tarjeta (doble clic) → pestaña **Recordatorios**, o bien clic derecho
   sobre la tarjeta → **🔔 Recordatorios…** (la entrada está desactivada si la
   tarjeta todavía no tiene fecha límite: primero ponle vencimiento).
2. Pulsa **Calendario…**: se abre la **cuadrícula del mes** con los días
   seleccionables **desde hoy hasta la fecha límite**. Marca o desmarca los días
   que quieras (los que ya tienen recordatorio vienen marcados).
3. Atajos de arriba: **Diario** marca **todos** los días del rango y **Cada
   semana** marca un día por semana (el mismo día de la semana que la fecha
   límite) — cómodo cuando el vencimiento cae varias semanas después.
   **Limpiar** desmarca todo (guardar sin días = borrar los recordatorios).
4. **Horario** es **opcional**: vacío significa «ese día» (el aviso aparece en
   cuanto arranques ese día), o escribe una hora (`09:00`, `18:30`) para que el
   aviso llegue a esa hora. La misma hora se aplica a los días elegidos.
5. **Guardar recordatorios** confirma la lista completa como **un paso de
   deshacer**.

En la pestaña tienes además **Añadir día** (un día concreto con su hora, útil
para un recordatorio suelto), la **lista** de los programados y, en cada fila, la
casilla **atendido** (un recordatorio ya atendido no vuelve a avisarte nunca) y
el botón **✖** para quitarlo.

**Cómo se te avisa**

- Al **arrancar** la app, y luego **cada minuto** mientras esté abierta, se
  revisan los recordatorios vencidos. Si los hay, aparece la ventana
  **Recordatorios pendientes** con una fila por momento: la **fecha** (o «Hoy»)
  y la **hora**, y debajo el **título de la tarjeta**.
- En cada fila: la casilla **atendido** (✓ lo marca como atendido y sale de la
  lista), **Abrir tarjeta** (abre el detalle) y **Marcar todo como atendido**
  (todas de una vez, un solo Ctrl+Z). **Más tarde** cierra la ventana: los
  recordatorios **siguen pendientes** (en su tarjeta y en la banda), pero la
  ventana no vuelve a aparecer sola en esa sesión.
- Mientras haya algo pendiente, arriba verás una **banda discreta**
  «🔔 N recordatorio(s) pendiente(s)»; al pulsarla se abre la ventana.
- El **frente de la tarjeta** muestra «🔔 n» con cuántos recordatorios tiene:
  resaltado mientras alguno está pendiente, en gris cuando ya los atendiste
  todos. Pasar el ratón por encima dice cuántos hay y cuántos quedan pendientes.

**Detalles que conviene saber**

- Un recordatorio vencido **te espera**: si la app estaba cerrada a esa hora, lo
  verás al próximo arranque (nunca se pierde), pero **nunca insiste** dos veces:
  al marcarlo como atendido desaparece para siempre.
- Un recordatorio **no puede quedar después de la fecha límite**. Si **adelantas
  el vencimiento** de una tarjeta, los recordatorios que quedaban más allá se
  descartan (y Ctrl+Z lo revierte); si le **quitas la fecha límite**, se
  descartan todos, porque sin vencimiento no hay recordatorios.
- Todo viaja con la tarjeta: se guarda en la base de datos, entra en el
  **deshacer/rehacer**, se **exporta/importa** con el tablero y se **fusiona** en
  la sincronización (atender un recordatorio en un dispositivo lo atiende en los
  demás).

## 9. Selección múltiple y acciones en lote

1. Pulsa **☐** en la cabecera de una columna para entrar en **modo selección**.
2. Marca las tarjetas que quieras (la barra muestra «N seleccionada(s)»).
3. Acciones en lote disponibles:
   - **Añadir o quitar etiquetas** (separadas por espacios o comas),
   - **Cambiar color**,
   - **Asignar proceso**,
   - **Mover a otra columna** (respeta el límite WIP del destino),
   - **Eliminar seleccionadas** (con confirmación),
   - **Salir de la selección**.

## 10. Filtros

En la barra de filtro:

- **Filtrar por etiquetas:** escribe etiquetas (autocompletado; separadas por
  comas).
- **AND / OR:** `AND` muestra tarjetas con **todas** las etiquetas; `OR`, las
  que tengan **alguna**; vacío, todas.
- **★ / !:** muestra solo las tarjetas **Importantes** y/o **Urgentes** (si
  activas ambos, exige los dos).
- **Proceso:** «Proceso: todos» / «Proceso: ninguno» / un proceso concreto.
- **✕ Quitar filtro:** limpia etiquetas, modo y filtro de proceso de una vez.

Atajo útil: **clic en el chip de una etiqueta** de cualquier tarjeta filtra
por esa etiqueta al instante; clic sobre el mismo chip otra vez lo quita.

**Color de las etiquetas:** menú **Etiquetas → Gestionar etiquetas...**
(§catálogo global). Al elegir una etiqueta puedes darle un **color** de la
paleta o dejarla en **Automático** (color por defecto, derivado del nombre). El
color elegido pinta el **chip** de esa etiqueta en **todos** los tableros, en
claro y en oscuro.

Los filtros se combinan entre sí.

### Búsqueda global (Ctrl+Shift+F)

Con la app llena de tableros, **🔍** (o **Ctrl+Shift+F**, o **Ver -> Buscar en
todos los tableros...**) busca en **todos los tableros a la vez**: título,
descripción, notas, etiquetas y listas de tareas. Los resultados aparecen
mientras escribes, con el tablero, el título y el campo donde coincidió más un
fragmento del texto.

- **Intro** o doble clic abre el resultado: el tablero pasa a primer plano, la
  tarjeta queda marcada como **actual** (la que mueven las flechas) y se
  limpian los filtros para que sea visible.
- Se muestran hasta **20 resultados por tablero** (el tablero activo primero);
  si se recorta, el propio contador lo dice.
- Buscar **no cambia** el tablero activo hasta que abres un resultado.

## 11. Procesos y vista de procesos

Un **proceso** es una agrupación transversal de tarjetas (p. ej. «Mudanza»),
independiente de la columna.

- **Menú Procesos:** **Nuevo proceso...**, escoger/renombrar/eliminar
  procesos, y **Filtrar por proceso** (todos / ninguno / uno concreto).
  Al eliminar un proceso, sus tarjetas se conservan **sin proceso**.
- **Asignar tarjetas a un proceso:** en el diálogo de la tarjeta (campo
  **Proceso**) o en lote (§9).

**Vista de procesos** (botón ⇄): muestra **una fila por proceso**, con sus
tarjetas de izquierda a derecha unidas por flechas según el orden de
precedencia. En cada tarjeta:

- 2 flechas **superiores**: crear una tarjeta **nueva** antes/después.
- 2 flechas **inferiores**: enlazar una tarjeta **existente** antes/después.

La vista también admite teclado: `←/→` mover · `Ctrl+←/→` tarjeta nueva ·
`Shift+←/→` enlazar existente · `Ctrl+Shift+←/→` desenlazar · `Supr` quitar
todos los enlaces · `Esc` salir.

## 12. Precedencias (dependencias entre tarjetas)

En el diálogo de la tarjeta, **Tareas previas** y **Tareas posteriores**
definen el orden lógico (multi-selección con **Ctrl**).

- El frente muestra contadores **←n** (precedentes) y **→n** (posteriores).
- La app **impide ciclos** (directos y transitivos) con un error claro.
- En el menú **⋯** de una columna, **Orden sugerido** reordena por orden
  topológico (si hay un ciclo, avisa y lista las tarjetas que quedaron
  fuera).

## 13. Seguimiento de tiempo

En la pestaña **Tiempo** del detalle:

- **Iniciar cronómetro** / **Detener cronómetro** (un registro por clic).
- Mientras corre, verás «En curso desde <hora>».
- Puedes escribir un **comentario** que se guarda al detener.
- Lista de **registros** con **Eliminar** (los registros cerrados no se
  editan, solo se borran) y **Tiempo total**.

Todo el seguimiento de tiempo es **deshacible**.

### Informe de tiempo (Ver -> Informe de tiempo...)

El cronómetro alimenta un **informe** de solo lectura del **tablero activo**:
**Ver -> Informe de tiempo...** abre una ventana no modal con dos tablas.

- **Tiempo por proceso**, de más a menos tiempo y con el número de tarjetas, y
  una última fila **Sin proceso** con lo que no pertenece a ningún proceso.
- **Tiempo por semana** (semanas ISO, de lunes a domingo), con las semanas sin
  registros a `0:00` para que se vean los huecos.

El rango por defecto son las **4 últimas semanas**; puedes elegir 8, 12 o
**todo el histórico**, y el botón ↻ recalcula (si hay un cronómetro
corriendo, cuenta **hasta ahora**).

Dos exportaciones escriben exactamente lo que ves: **Exportar CSV...**
(separador `;` y fechas ISO, listo para Excel) y **Exportar PDF...**.

Reglas del informe: una entrada cuenta en el rango en el que **empezó**, y una
entrada de duración cero no aparece. La columna **Minutos** lleva enteros, para
poder sumarlos en una hoja de cálculo.

## 14. Apariencia: tema y colores

**Archivo → Preferencias**, con la pestaña **Apariencia**: tema **Claro /
Oscuro**, colores de **fondo** y **texto**, **colores de resalte** de las
señales (Importante, Urgente, Importante y urgente — con botón
**Restablecer valores**) y **reglas de color de etiquetas** (añadir, editar o
quitar combinaciones de etiquetas). La antigua pestaña de **imagen de fondo**
se retiró en la Sesión 11: hoy la apariencia es solo color.

El **modo oscuro** también se cambia al vuelo con el botón 🌙 o **Ctrl+D**.

## 15. Idioma

**Menú Idioma:** English, Español, Deutsch, Français. Se aplica al instante y
se recuerda entre arranques.

## 16. Atajos de teclado

Pulsa **F1** (o **Ayuda → Atajos de teclado...**) para ver esta tabla dentro
de la app:

| Atajo | Acción |
|---|---|
| **Ctrl+Z** | Deshacer |
| **Ctrl+Shift+Z** | Rehacer |
| **Ctrl+1 / Ctrl+2 / Ctrl+3** | Vista de tarjeta: solo título / + resumen / completa |

| **Ctrl+Shift+F** | Buscar en todos los tableros |
| **Ctrl+Shift+1 / Ctrl+Shift+2** | Vista de tablero / vista de procesos |
| **Ctrl+L** | Añadir o quitar etiquetas a lo seleccionado (o a la tarjeta actual) |
| **Ctrl+N** | Nuevo tablero |
| **Ctrl+F** | Enfocar el filtro de etiquetas |
| **Ctrl+P** | Enfocar el filtro de proceso |
| **Ctrl+Shift+P** | Mostrar tarjetas de todos los procesos (quitar filtro) |
| **Ctrl+Shift+N** | Mostrar solo tarjetas sin proceso |
| **Ctrl+D** | Cambiar modo oscuro |
| **Ctrl+S** | Guardar ahora (confirma el estado) |
| **Ctrl+Q** | Salir |
| **F1** | Ayuda de atajos |

> En la **vista de procesos** hay atajos adicionales (§11).

## 17. Deshacer/Rehacer y guardado

- **Deshacer/Rehacer** con Ctrl+Z / Ctrl+Shift+Z, botones ↶ ↷ de la barra, o
  el menú. Cada mutación es **una** transacción deshacible (crear/editar/
  borrar, mover, etiquetas en lote, checklist, procesos, tiempo…).
- El historial es **persistente por tablero** y **sobrevive al reinicio**;
  vive en la carpeta `history/` junto a la base de datos.
- **Guardado:** cada cambio se confirma en SQLite **al instante**
  (persist-first). **Ctrl+S** (o 💾) solo **confirma** el estado con un
  mensaje («Todo está guardado en la base de datos»). No hay trabajo perdido
  por olvidar guardar.

## 18. Archivos, datos y portabilidad

| Modo | Ubicación |
|---|---|
| Por defecto | `~/.personalkanban/` (`kanban.db`, `history/`) |
| Portátil (lanzadores) | `<unidad>/data/` junto al jar |
| A tu elección | Donde elijas en **Archivo → Nueva/Abrir base de datos...** |

- El menú **Archivo** muestra la **ruta del archivo actual** y las **bases
  recientes** (últimas 5). La última usada se recuerda (config local de cada
  equipo).
- La base usa el **diario de rollback** de SQLite (no WAL): al **cerrar
  limpiamente** el diario se integra y quedan solo los archivos de la base.
- **Consejo para nube:** cierra la app **antes** de cambiar de equipo o de
  sincronizar la carpeta con Drive/Dropbox/OneDrive; así SQLite termina de
  escribir y el `.db` queda completo. Nunca uses el `.db` por carpeta
  compartida como método de sincronización "en vivo" (ver §20).

## 19. Exportar e importar

- **Exportar tablero (JSON)...** — guarda un tablero como `.json` portable
  (incluye columnas, tarjetas, notas, checklist, procesos, precedencias y
  tiempo).
- **Exportar tablero a PDF...** — genera un PDF paginado del tablero (formato
  póster).
- **Importar tablero (JSON)...** — crea un tablero a partir de un `.json`
  exportado.

La carpeta de exportación/importación se recuerda.

## 20. Sincronización online (opcional)

> Requiere un servidor con la API de sync (por ejemplo el backend PHP
> incluido en `sync/`, desplegable con `deploy-sync-api.bat`). Si no la usas,
> la app funciona **100 % local**.

**Configurar:** **Archivo → Sincronización online (API)...**

- **URL del servidor** (p. ej. `http://localhost/sync/`) y **Clave de API**.
- **Probar conexión** (en segundo plano) → «Conexión correcta: N tablero(s)».
- **Cargar catálogo** → lista los tableros del servidor.
- **Desactivar sincronización** → la app sigue funcionando en local.

La línea de estado **«Sincronización: …»** en el menú **Archivo** indica si
está activa.

**Sincronizar:** **Archivo → Sincronizar ahora**. Corre en segundo plano y
muestra un aviso discreto al terminar:

- *Tablero subido al servidor por primera vez.*
- *Cambios locales subidos.*
- *Tablero actualizado desde el servidor.*
- *Cambios de ambos lados combinados.*

**Conflictos:** si tú y el servidor cambiaron lo mismo, la app **combina** los
cambios (por campo, sin perder lo que solo cambió un lado) y **conserva la
otra versión** en una **copia de conflicto**:

```
<carpeta de la base de datos>/conflicts/<boardId>-<fecha>.json
```

El aviso indica cuántos conflictos hubo y la ruta de la copia. Las copias son
archivos JSON que puedes abrir y revisar.

**Privacidad y errores:** la sincronización solo envía el **contenido del
tablero activo** (nunca el `.db`). Los errores más comunes se traducen a
mensajes claros (sin red, 401 clave rechazada, 413 demasiado grande, error  del servidor). *Recomendación:* no sincronices el archivo `.db` por
  Drive/Dropbox como sustituto; usa esta función de red.

## 21. Preguntas frecuentes

- **¿Tengo que guardar?** No. Cada cambio se guarda al instante; Ctrl+S solo
  te lo confirma.
- **¿Dónde está mi dato?** En `kanban.db` (y `history/`). Ver §18.
- **¿Funciona sin internet?** Sí, siempre. La sync online es opcional.
- **¿Puedo usar la misma base en varios equipos?** Sí, llevando el archivo
  (o usando un USB/carpeta sincronizada **con la app cerrada**), o mediante
  la **sincronización online**.
- **¿Se puede recuperar algo borrado?** Sí, con **Ctrl+Z** (el historial
  sobrevive al reinicio). Los cambios de sync no contaminan el historial.
- **¿Qué es «Marcar como finalizada»?** Una marca por tablero (normalmente la
  columna «Hecho») para distinguirla visualmente.

## 22. Solución de problemas

- **No arranca / falta JavaFX:** usa el jar **portátil** (incluye JavaFX), o
  arranca con `mvn javafx:run` en desarrollo con JDK 21+.
- **«La URL no es válida» / no conecta:** revisa que la URL acabe con `/`
  (p. ej. `http://localhost/sync/`) y que el servidor esté levantado y la
  clave sea la correcta.
- **Aviso «SQLite está ocupado»:** los datos están a salvo en el diario y se
  integran solos; vuelve a intentarlo en unos segundos.
- **La descripción se ve mal en oscuro:** cambia el tema con Ctrl+D; los
  temas claro y oscuro están ajustados para legibilidad.
- **Un equipo no ve los cambios de otro (sin sync online):** cierra la app en
  el primer equipo antes de llevarte el archivo; SQLite necesita terminar de
  escribir.

---

*¿Algo falta o no coincide con lo que ves? Anótalo y lo corregimos en la
siguiente sesión. Lo pendiente y el plan de trabajo viven en
[`../PLAN.md`](../PLAN.md).*
