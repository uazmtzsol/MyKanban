# Para crear el instaler en Mac
- En una carpeta vacia con solo el .jar y el archivo .icns
jpackage \
  --type app-image \
  --name "Personal Kanban" \
  --input . \
  --main-jar personal-kanban.jar \
  --main-class com.personalkanban.Launcher \
  --icon PersonalKanban.icns \
  --dest ~/Desktop
  
## Para quitar el símbolo de "prohibido"
¡Excelente noticia que la aplicación ya se ejecute correctamente! Eso confirma que el empaquetado con `jpackage` funcionó a la perfección.

El símbolo de "prohibido" (🚫) superpuesto en el ícono es un comportamiento clásico de macOS y **no significa que la app esté rota** (como bien notaste, sí funciona). Casi siempre se debe a una de estas dos razones:

1. **Falta de permisos de ejecución** en el archivo binario interno de la app.
2. **Atributo de cuarentena** (`quarantine`) que macOS le asigna automáticamente a archivos nuevos o descargados, confundiendo al Finder.

Vamos a quitarle ese símbolo en 3 pasos rápidos desde la Terminal. 

*(Nota: Asumiré que ya moviste la app a la carpeta de Aplicaciones como indicamos antes. Si todavía está en el Escritorio, cambia `/Applications/` por `~/Desktop/` en los comandos).*

---

### Paso 1: Quitar el atributo de cuarentena
A veces macOS marca la app como "sospechosa" solo por cómo se creó. Este comando limpia esa etiqueta:

```bash
xattr -rd com.apple.quarantine "/Applications/Personal Kanban.app"
```
*(No te preocupes si este comando no devuelve ningún mensaje, significa que funcionó o que la app no tenía esa etiqueta).*

### Paso 2: Corregir los permisos de ejecución
Este es el culpable el 95% de las veces. Le diremos a macOS que el ejecutable interno tenga los permisos correctos para que el Finder deje de mostrar el 🚫:

```bash
chmod +x "/Applications/Personal Kanban.app/Contents/MacOS/Personal Kanban"
```

Para estar 100% seguros, podemos aplicar permisos seguros a toda la carpeta de la app:

```bash
chmod -R u+rwX,go+rX,go-w "/Applications/Personal Kanban.app"
```

### Paso 3: Reiniciar el Finder para actualizar el ícono
A veces el Finder guarda en caché el ícono "malo". Forzar su reinicio lo obligará a leer el ícono limpio:

```bash
killall Finder
```
*(La pantalla parpadeará un instante y el escritorio se recargará. Es completamente normal y seguro).*

---

### ¿Sigue apareciendo el símbolo?
Si después de estos 3 pasos el símbolo 🚫 persiste, hay una última posibilidad: que el archivo `.icns` que usaste originalmente ya tuviera ese símbolo "dibujado" o incrustado en la imagen (a veces pasa si se descargó un ícono de internet que ya venía con ese badge).

**Prueba rápida para descartarlo:**
1. Ve a `/Applications/`, haz clic derecho en **Personal Kanban** y elige **Obtener información** (`Cmd + I`).
2. En la esquina superior izquierda de la ventanita, haz clic en el ícono pequeño (se pondra un borde azul alrededor).
3. Presiona `Cmd + V` (pegar) *sin haber copiado nada nuevo*, o simplemente arrastra un ícono `.icns` nuevo y limpio que sepas que no tiene el símbolo.
4. Cierra la ventana de información.

Con los comandos de permisos (Paso 2), el 99% de las veces el símbolo de prohibido desaparece de inmediato. ¡Cuéntame si con esto tu ícono ya se ve perfecto! 🎯