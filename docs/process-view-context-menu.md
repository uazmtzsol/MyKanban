# Menú contextual en la vista de procesos — lluvia de ideas (💬 sin implementar)

Estado: **discusión abierta**. Este documento es un registro de ideas, no un
plan cerrado. No implementar hasta acordar.

Contexto: en §7.8 se acordó (y ya se hizo) **A1 + B1**: en la vista de procesos
un clic selecciona/enfoca y un doble clic abre el detalle, y el inspector pasó
a ser una sola banda al pie. Quedó pendiente discutir el punto **C2: el menú
contextual** (clic derecho) de las tarjetas de esa vista, porque **hoy no
existe ninguno**: el clic derecho sobre una tarjeta del diagrama no hace nada.

---

## 1. Inventario: qué existe hoy

### 1.1 Menú contextual de la tarjeta en el tablero (`CardViewBuilder`)
Orden actual:

1. **Modo de vista de esta tarjeta** (3 radios + «usar el del tablero»)
2. *— separador —*
3. **Copiar tarjeta** (T10)
4. **Mover a otro tablero...**
5. **Exportar tarjeta...**
6. **Archivar / Desarchivar**
7. **Marcar como terminada / Reabrir** (T24)
8. **Reagendar ▸** (hoy / mañana / semana próxima / lunes próximo / quitar fecha)
9. **🔔 Recordatorios…** (deshabilitado sin fecha límite)
10. **Proceso ▸** (radios de procesos + «Sin proceso»)

> Nota: "Abrir detalle", "Editar" y "Eliminar" **no** están en el menú
> contextual de la tarjeta del tablero (se alcanzan con doble clic, botones ✎/✕
> y el menú **Tarjetas**).

### 1.2 Menú **Tarjetas** de la barra (actúa sobre la tarjeta «actual»)
`open, edit, copy` · `Reagendar, Recordatorios, Proceso` · `terminar, archivar,
exportar, mover a otro tablero` · `eliminar`.

### 1.3 Acciones que **solo** existen en la vista de procesos
- **Crear tarjeta nueva** antes/después (flechas verdes).
- **Enlazar tarjeta existente** antes/después (flechas azules).
- **Desenlazar** una relación (clic en conector grueso, con confirmación).
- **Quitar todos los enlaces** de una tarjeta (`Supr`).
- **Orden sugerido** del proceso (`Proceso ▸`… / `onShowSuggestedOrder`).

### 1.4 Lo que ya hacen las flechas de cada tarjeta del diagrama
Dos arriba (crear nueva antes/después) y dos abajo (enlazar existente
antes/después). Ese espacio de la tarjeta **ya está muy cargado** de
affordances.

---

## 2. Ideas (lluvia, sin filtrar)

### Bloque A — «Espejo del tablero»
- **A-1** Reproducir **exactamente** el menú contextual del tablero (§1.1),
  sin más.
- **A-2** Reproducirlo pero **quitando** lo que no aplica aquí (modo de vista
  de tarjeta, copiar/pegar por columna).
- **A-3** Reproducirlo y **añadir** siempre las acciones de proceso (§1.3).

### Bloque B — «Menú específico de proceso»
- **B-1** Un menú **centrado en el diagrama**: enlazar/crear antes-después,
  desenlazar, quitar todos los enlaces, orden sugerido.
- **B-2** Añadir **enfocar** la tarjeta y **abrir detalle / editar**.
- **B-3** Submenús `Enlazar ▸` y `Crear ▸` que **reemplacen** las cuatro
  flechas (reducir la carga visual de la tarjeta).

### Bloque C — «Menú híbrido» (espejo + proceso)
- **C-1** Dos secciones separadas: **Tarjeta** (espejo) y **Proceso** (enlaces).
- **C-2** Una sola lista plana con separadores, orden «primero lo frecuente».
- **C-3** Un submenú **Proceso ▸** que ya existe (asignar a proceso) **más** un
  submenú nuevo **Relaciones ▸** (enlazar/crear/desenlazar).

### Bloque D — Interacción y ergonomía
- **D-1** Clic derecho **también selecciona/enfoca** la tarjeta (como el clic
  izquierdo), para que el menú y el inspector apunten a la misma tarjeta.
- **D-2** Clic derecho **no cambia** el foco; el menú actúa solo sobre la
  tarjeta bajo el cursor.
- **D-3** Si hay **varias tarjetas** en foco/selección, mostrar acciones en lote.
- **D-4** Añadir **teclas de acceso** (Alt+…) y **atajos** a las entradas.
- **D-5** Que el menú sea alcanzable con teclado: tecla Menú / Shift+F10.

### Bloque E — Coherencia con lo ya hecho (A1/B1)
- **E-1** El elemento **cabecera** del inspector tiene su propio mini-menú
  (clic derecho sobre el título del inspector).
- **E-2** Reutilizar **los mismos métodos** de `BoardController`
  (`onProcessAddNewRelation`, `onProcessLinkExisting`, `onArrangeArrow`,
  `onProcessUnlinkAll`) para que el diagrama y las flechas no diverjan.
- **E-3** Reutilizar `CardViewBuilder.rescheduleMenu` / `processMenu` en vez
  de escribir variantes.

### Bloque F — Extras (más allá del menú, pero relacionados)
- **F-1** **Menú de conector** (clic derecho sobre una flecha) con
  enlazar/desenlazar/crear en medio.
- **F-2** **Menú de proceso** (clic derecho sobre la cabecera `#proceso`):
  renombrar, eliminar, orden sugerido, filtrar por este proceso.
- **F-3** Arrastrar una tarjeta del diagrama para **reordenar/religar**.
- **F-4** «Convertir en subtarea / promocionar» (fuera de alcance aquí).

---

## 3. Contradicciones y tensiones detectadas

1. **A-1 vs B-2/A-2 — ¿el menú es «el del tablero» o «el del proceso»?**
   El usuario pidió C2 justamente porque la vista de procesos es distinta;
   clonar tal cual el del tablero ignora que aquí **la relación es la acción
   principal** y que hay cosas del tablero (modo de vista por tarjeta) que
   aquí no tienen sentido.

2. **B-3 vs §1.4 (carga visual) — quitar flechas vs mantenerlas.**
   Reemplazar las cuatro flechas por submenús reduce ruido, pero **pierde el
   gesto directo de un clic** que hoy funciona y que el usuario conoce
   (documentado en el manual §11). Cambiarlo es una regresión de hábito.

3. **D-1 vs D-2 — qué hace el clic derecho con la selección.**
   Que el clic derecho **no** cambie el foco permite operar sobre una tarjeta
   sin perder la actual (útil para encadenar), pero entonces **puede haber dos
   tarjetas «señaladas»** a la vez (la del clic derecho y la actual resaltada),
   lo que confunde: ¿a cuál apunta «Quitar todos los enlaces»?

4. **A-3 vs E-3 — duplicación de entradas de proceso.**
   El menú contextual ya tiene **Proceso ▸** (asignar a proceso). Si además se
   añade **Relaciones ▸**, el usuario ve dos submenús con «proceso» en el
   nombre y significados distintos (pertenencia al proceso vs precedencia).

5. **D-3 (lote) vs A1/B1 — la vista no tiene modo selección.**
   El tablero tiene modo lote (§9) con su barra; la vista de procesos **no**
   tiene selección múltiple. Ofrecer «acciones en lote» implicaría **inventar**
   un modo selección aquí, que hoy no existe: alcance no trivial.

6. **E-2 (reutilizar) vs la firma de los métodos.**
   Los métodos de proceso toman `CardId` y algunos abren diálogos modales; para
   el menú contextual habría que **deshabilitar** entradas según el estado
   (p. ej. «quitar todos los enlaces» sin enlaces, «desenlazar» sin relación en
   ese lado), lo que es lógica nueva aunque la acción se reutilice.

7. **A-1 vs A2 de §7.8 — coherencia de gestos.**
   Ya quedamos que en esta vista **un clic = seleccionar / doble = abrir**. Un
   menú contextual con «Abrir detalle» y «Editar» es coherente; pero si además
   copiamos entradas como «Modo de vista de esta tarjeta», el usuario podría
   esperar **más** espejo del tablero del que conviene.

---

## 4. Sugerencias desde cada punto de vista

### 4.1 Funcional
- El valor diferencial de esta vista es **la relación**. El menú debe priorizar
  enlazar/crear/desenlazar por encima de lo administrativo.
- Mantener **una sola fuente de verdad** por acción (E-2/E-3): nunca dos rutas
  que hagan lo mismo con código distinto.
- Deshabilitar (no ocultar) lo que no aplica, explicando por qué en el tooltip
  (patrón ya usado en «Recordatorios…» sin fecha límite).

### 4.2 UX
- **No duplicar** las cuatro flechas como cuatro entradas sueltas: agrupar en
  `Enlazar ▸` y `Crear ▸` **solo si** se decide que el menú es la vía
  principal; si las flechas se quedan, el menú es el **atajo alternativo**.
- **Separar por significado**, no por fuente: sección «Tarjeta» y sección
  «Relaciones» (C-1), para que «Proceso ▸» (pertenencia) y «Relaciones ▸»
  (precedencia) no se confundan.
- Etiquetas **con verbo y contexto**: «Enlazar tarjeta existente antes» en vez
  de «Antes».

### 4.3 Ergonomía
- **Clic derecho = seleccionar y enfocar** (D-1) y **luego** abrir el menú: un
  solo estado «señalado» elimina la ambigüedad del punto 3 de §3. Es el patrón
  de la mayoría de apps (el clic derecho «apunta» a lo que muestra).
- Reservar **Shift+F10 / tecla Menú** para abrir el mismo menú con teclado
  sobre la tarjeta enfocada (D-5), sin inventar atajos nuevos.
- No añadir **atajos nuevos** al menú contextual salvo los que ya existen; los
  del diagrama ya están en las flechas y en `Ctrl/Shift+←/→`.

### 4.4 Usabilidad / accesibilidad
- El manual §11 ya documenta el teclado del diagrama: si el menú replica esas
  acciones, **el manual no debe duplicar** la explicación; conviene un párrafo
  «clic derecho» que remita a la sección de teclado.
- Comprobar **contraste** del menú sobre el lienzo (fondo del diagrama) en tema
  claro y obscuro (mismo control que se hizo para las bandas en T24 · B1).
- El elemento «cabecera» del inspector (E-1) es **opcional** y, si se añade,
  debe ser **el mismo menú** que el de la tarjeta (no una variante).

### 4.5 Consistencia con el resto de la app
- Reutilizar literalmente las **mismas cadenas i18n** que el menú del tablero
  allí donde la acción coincida (evita traducciones divergentes ×5).
- Si una acción solo tiene sentido en un lado (p. ej. «Modo de vista de la
  tarjeta»), **dejarla fuera** de este menú (principio de menor sorpresa).

---

## 5. Reconstrucción recomendada (a discutir, no cerrada)

**Opción que mejor reconcilia las tensiones** — llamémosla **C-1 híbrida**:

```
Enfocar en el diagrama
Abrir detalle            (doble clic)
Editar tarjeta
────────────────────────
Enlazar tarjeta existente ▸   antes · después
Crear tarjeta nueva ▸         antes · después
Desenlazar relación ▸         (lista de vecinos)
Quitar todos los enlaces
Orden sugerido del proceso   (>1 tarjeta)
────────────────────────
Reagendar ▸
🔔 Recordatorios…
Proceso ▸
────────────────────────
Terminar / Reabrir
Archivar / Desarchivar
Copiar tarjeta
Exportar tarjeta…
Mover a otro tablero…
────────────────────────
Eliminar tarjeta…
```

Y las decisiones de interacción:
- **D-1** clic derecho selecciona+enfoca y luego abre el menú.
- **Enter/Shift+F10** abren el mismo menú con teclado.
- **Las cuatro flechas se quedan** (no se reemplazan): el menú es el atajo
  alternativo, no la vía única → evita la regresión de hábito del punto 2.

Acciones **fuera** del menú (deliberadamente): modo de vista de tarjeta
(no aplica al diagrama) y copiar/pegar **por columna** (no hay columnas aquí;
«copiar tarjeta» sí entra).

---

## 6. Preguntas abiertas para la conversación

1. ¿El clic derecho **selecciona** la tarjeta (D-1) o no la toca (D-2)?
2. ¿Las **cuatro flechas** se quedan (menú = alternativa) o se **retiran**
   (menú = vía única)?
3. ¿Se ofrece una sección **Relaciones** separada de **Proceso** (evita la
   confusión de §3.4), o basta un único submenú de proceso?
4. ¿Se añade **menú de conector** (F-1) y **menú de cabecera de proceso** (F-2)
   en la misma ronda, o se dejan para después?
5. ¿Hace falta **selección múltiple** en esta vista (D-3), o el diagrama es por
   naturaleza de una tarjeta a la vez?
6. ¿Qué acciones **no** deben aparecer aquí para no sorprender (modo de vista,
   copiar/pegar por columna, «Eliminar…» con confirmación)? 
