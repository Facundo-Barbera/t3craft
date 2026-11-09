# Plan: interfaces, navegador e integraciones

Rama `research/game-ui-integrations`, 2026-11-09. Este hilo es de investigación y docs: no se tocó código del mod, no se
compiló y no se abrió ninguna imagen.

## Estado

**Plan aprobado por Facundo (2026-11-09).** El coordinador lo toma como siguiente frente **cuando terminen la limpieza
(issue #5) y la migración del repo**. Hecho ya, en este hilo y solo en docs:

- **Fase 0 del frente A escrita en `docs/dev.md`** (sección "UI cycle", más el diagnóstico en "Hot-swap"): cliente de
  desarrollo en Windows nativo con el IDE del mismo lado y en depuración, qué recarga F3+T, T4 por defecto (compilar en la PC,
  jugar en el Mac), WSL solo para renders, y las cinco causas de que un hot-swap no haga nada.
- **Q1 aprobada:** los tokens de UI pasan a `ui-style.json`, **después del issue #5**.
- **Q5 aprobada:** el **coordinador** actualiza `AGENTS.md` con la regla del cliente de Windows. Este hilo **no toca
  `AGENTS.md`**.
- **Issues creados** a partir de §10 (ver esa sección).
- Las otras seis preguntas de §9 **siguen abiertas** hasta la fase que bloquea cada una.

Nada del código del mod está implementado: todas las fases 1 a 7 están por hacer.

- **Investigación de respaldo:** `docs/research/game-ui-and-integrations.md` (catálogo completo, 14 anexos, con las fuentes y
  lo que quedó por verificar). Este plan es la versión ordenada y decidida de ese material.
- **Fuera de alcance:** el issue [#1](https://github.com/Facundo-Barbera/t3craft/issues/1) (T3 Connect o una CLI de T3 Code como
  backend del mod) lo investiga otro hilo. Aquí solo se señala dónde toca.
- **Marca (verificar):** no se pudo confirmar sin compilar o sin abrir el juego. Ninguna fase arranca con un (verificar) crítico
  sin resolver.

---

## 1. Decisiones ya tomadas (Facundo, 2026-11-09)

| # | Decisión | Consecuencia |
|---|---|---|
| D1 | Las interfaces del mod se **unifican y pulen**, tirando a la del chat (Settings, Pairing, confirmaciones…) | Frente B. Es trabajo nativo, independiente del navegador |
| D2 | El navegador **no reemplaza** nuestras interfaces: es para **cosas de fuera** (Gmail, Google Chat, GitHub…) | Frente C. Invalida el plan de "T3 web dentro del juego" (§10 de la investigación, marcado como descartado) |
| D3 | Las pantallas físicas de navegador en el mundo serían **decorativas**; lo útil es **clic derecho → abre la interfaz, y esa interfaz es el navegador** | El navegador se construye como pantalla, no como bloque. Los bloques quedan para después |
| D4 | **Varios bloques útiles** es una idea aparte | Frente E, sin prioridad asignada |
| D5 | Se quiere el **ciclo con hot reload**, y **sin añadir otro mod**: solo lo que trae Fabric | Frente A. Descarta `fabric-hot-reload` |
| D6 | Se quiere **ver la UI en el Mac** | Frente A |
| D7 | El **cliente de desarrollo del PC pasa a Windows nativo**; WSL se queda con los renders | Frente A. Obliga a corregir `AGENTS.md` (ver 6.1) |
| D8 | **T4 por defecto para mirar la UI**: el PC compila, el jar viaja al Mac, se juega nativo en el Mac | Frente A. Imagen pixel-exacta, sin HotSwap en el Mac |
| D9 | El ciclo de UI se **documenta en `docs/dev.md`** | Tarea A3 |

**Decisión técnica derivada (no de Facundo, pero ya cerrada por los datos):** para un navegador de verdad hace falta
**CEF**, con **Rinku** o **MCEF Modern** (los dos declaran 26.3, LGPL-2.1, descarga de Chromium de 136 a 150 MB en el primer
arranque). El stream del preview de T3 (vía A de la investigación) no sirve para Gmail: solo muestra pestañas que abre el
servidor de T3.

---

## 2. Los cinco frentes

Son **independientes salvo donde se diga**. Esto importa: el pulido de la UI no espera al navegador, y al revés.

| Frente | Qué es | Depende de |
|---|---|---|
| **A. Ciclo de desarrollo de UI** | Hot reload que funcione y ver la UI desde el Mac | Nada. **Habilita todo lo demás** |
| **B. Unificar y pulir las interfaces nativas** | Kit propio, un solo UI scale, fuente, iconos; migrar las pantallas que usan widgets vanilla | A (para iterar), y coordinación con la limpieza (#3–#8) |
| **C. Navegador para cosas de fuera** | Pantalla con CEF, lanzada con clic derecho | A. Nada de B |
| **D. Interfaces e integraciones nuevas** | Señales discretas, cola de atención, webhooks, sonido, toasts, diff | A. Algunas piezas usan el kit de B |
| **E. Bloques útiles** | Idea aparte de Facundo | B (para que los bloques abran pantallas del kit) |

---

## 3. Frente A — Ciclo de desarrollo de UI (va primero)

**Por qué primero:** cada cambio visual hay que verlo. Hoy el hot reload no funciona y la UI no se puede ver desde el Mac, así
que todo lo demás se paga dos veces.

### A1. Arreglar el camino oficial de Fabric (sin código)
Causas probables de que el hot reload nunca funcionara, en orden (detalle en §13.2 de la investigación):
1. **Nadie empuja el bytecode.** `DevReloadClient` solo **detecta** cambios y resetea estado; no redefine clases. Lanzando con
   `./gradlew runClient` desde la terminal no hay depurador atado y **nunca se recarga nada**. Hay que lanzar la run
   configuration de Loom **en modo depuración desde el IDE** y pulsar *Build Project*.
2. **IntelliJ compilando con Gradle:** síntoma típico "0 classes reloaded". Debe estar en *Build and run using: IntelliJ*.
3. **`-Pt3craft.hotswap` con un JDK normal:** la JVM **no arranca** (esa bandera solo existe en JetBrains Runtime). Se lee como
   "el hot reload está roto".
4. **JBR 25 tiene crashes abiertos** en la redefinición mejorada. Uno se dispara con `-XX:+UseCompactObjectHeaders`, que en
   JDK 25 **no está activado por defecto**, así que se evita no activándolo.
5. **El PC es WSL:** IDE en Windows y juego en WSL hacen que el depurador cruce la frontera. D7 lo resuelve.

Tareas: mover el cliente de desarrollo a **Windows nativo** con el IDE del mismo lado (D7), run configuration en depuración,
*Build and run using: IntelliJ*, y **sin** `-Pt3craft.hotswap` al principio. Resultado: HotSwap de cuerpos de método, que es lo
que la propia doc de Fabric cita para *"screen element alignment"*.
- Esfuerzo: **S**, sin código. Lo hace Facundo en su PC.
- **Dato del PC:** correr Minecraft dentro de WSL con WSLg no es buen camino (OpenGL por software, techo de ~72 fps por la
  copia de cuadros, inestabilidad reportada). De ahí D7.

### A2. Lo que Fabric da gratis y hoy no se aprovecha
| Mecanismo oficial | Recarga | Estado en el mod |
|---|---|---|
| HotSwap con el IDE en depuración | Cuerpos de método | Disponible tras A1 |
| **F3+T** | Texturas, sprites, nine-slice, modelos, fuentes, lang | **Ya funciona** para las sprites del chat |
| `/reload` | Datapacks | No aplica al cliente |
| `fabric-client-gametest-api-v1` | Capturas reales con entrada simulada | Compila, nunca se corrió; experimental |

**Fabric no trae recarga de código más allá del HotSwap de la JVM.** JBR es un JDK de terceros, no parte de Fabric.

### A3. Documentar el ciclo en `docs/dev.md` (D9)
Contenido: dónde corre el juego en el PC (Windows) y dónde los renders (WSL); los tres pasos de A1; qué recarga cada mecanismo;
el camino T4 al Mac; y la advertencia de 3.1 sobre juzgar píxeles.
- Esfuerzo: **S**, solo docs. Es lo único de este plan que se puede hacer ya sin aprobar nada más.

### A4. Ver la UI en el Mac — T4 por defecto (D8)
El PC compila (`./gradlew build`), el jar viaja por Tailscale, el Mac lo corre en una instancia normal de Minecraft.
- **Ventajas:** imagen **pixel-exacta** (sin compresión), nativo y fluido en el Mac, y respeta "Gradle solo en el PC".
- **Límite:** **sin HotSwap** (no es el cliente de Loom). F3+T sí sirve para recursos. El ciclo de iteración rápida vive en el
  PC; el Mac es para mirar bien.
- **Transporte:** el sshd del PC es Windows OpenSSH con `wsl.exe` como shell, así que `scp` y `rsync` no funcionan contra el PC
  (`AGENTS.md`). El patrón que sí funciona es el del relay de renders: un script por stdin (`ssh -T pc < script.sh`) que
  devuelve el jar en base64. Con el build en Windows, el script (que corre en WSL) lo lee en `/mnt/c/...`.
  **(verificar)** la ruta exacta del checkout de Windows cuando exista.
- Esfuerzo: **S**. Un script en `tools/` (por ejemplo `tools/dev/jar-to-mac.sh`) haría el viaje en un comando.

### A5. Respaldo: streaming (T1), si alguna vez hace falta interactividad desde el Mac
Sunshine en el PC y Moonlight en el Mac, los dos libres, por Tailscale. Queda **documentado pero no es el camino por defecto**
(D8). Detalles que importan si se usa: comprobar `tailscale status` → `direct` (un relay sube mucho la latencia); abrir los
puertos de Sunshine **solo en la interfaz de Tailscale** (el firewall de Windows la clasifica como red pública y los bloquea);
añadir el PC **a mano** en Moonlight (el mDNS no cruza el tailnet).

### 3.1 Advertencia que vale para todo el frente A
La fuente de Minecraft es un bitmap de 8 px: el texto chico y las líneas de 1 px son lo que peor sobrevive a la compresión de
vídeo. Lo pixel-exacto se juzga con **T4** o con **capturas** (`F2` escribe un PNG; el relay y Citadel Studio ya las traen al
Mac), nunca desde un stream.

---

## 4. Frente B — Unificar y pulir las interfaces nativas (D1)

**Diagnóstico.** El chat y `T3Screen` ya tienen kit propio (`ChatTheme`, `ChatDraw`, sprites nine-slice en
`textures/gui/sprites/chat/`). Settings, Environments, Pair, Confirm, NewThread y OperatePair heredan de `Screen` con
**widgets vanilla** (`Button`, `EditBox`, el toggle "ON/OFF" verde y rojo). Ese es el salto visual, y no es una limitación de
Minecraft: una `Screen` es Java arbitrario sobre un lienzo de píxeles.

### B1. Tokens de UI como recurso (la palanca de todo)
Mover colores y medidas de `ChatTheme` a `assets/t3craft/gui/ui-style.json`, **leído por el resource manager** con un reload
listener. Entonces **F3+T recarga la paleta y las medidas en caliente**, que es el 80 % del pulido visual, sin HotSwap, sin JBR
y sin depurador.
- **Es lo que hace AgentCraft, mejorado:** ya tiene sus tokens fuera del código (`gui/ui-style.json`, `palette.json`,
  `kit.json`) y su `UiStyle` dice *"Never hard-code a colour in a screen or renderer: ask here"*. Su límite es que los carga con
  `getResourceAsStream` en un **inicializador estático**: por classpath y una sola vez, así que F3+T no los recarga. Por el
  resource manager, nosotros sí tendríamos recarga en vivo.
- El repo ya usa el patrón dos veces: `HqLayoutPackWatcher` y `ConfigManager` (relee cada 2 s).
- Esfuerzo: **S a M**. Toca `ChatTheme` y sus consumidores. **Bloqueado por la pregunta Q1.**

### B2. Galería de pantallas: `/t3dev ui <escenario>`
Un registro de escenarios (los mismos fixtures de `uiRender`) que abre **cualquier** pantalla con datos falsos, sin red ni T3.
Reduce el coste de los cambios que sí necesitan reiniciar: vuelves a la pantalla exacta en un comando.
- Es lo que AgentCraft hace con su DevBridge (`dev.screen {open:"decision"}`) y escenas JSON.
- Esfuerzo: **S a M**.

### B3. Mini kit compartido
`T3Button`, `T3Toggle`, `T3Field`, `T3Select`, `T3List`, `T3Dialog`, `T3Tabs` sobre `ChatTheme`/`ChatDraw`, que ya existen.
- Esfuerzo: **M**.

### B4. Migrar las pantallas de widgets vanilla
Settings, Environments, Pair, OperatePair, Confirm, NewThread, Snooze → el kit. Donde tenga sentido, pasar a **paneles dentro de
`T3Screen`** en vez de pantallas separadas (como la página de ajustes de T3 Code), y los diálogos a modales del kit.
- Esfuerzo: **M a L**. **Es el frente que más choca con la limpieza en curso** (ver 6.2).

### B5. Un solo UI scale y una fuente
- **UI scale propio:** el GUI scale de vanilla es entero y global (no existe 2,5). El patrón es calcular `k = deseado / guiScale`,
  aplicar `pose().scale(k)` al dibujar y **dividir el ratón por `k`**. No afecta al resto del juego ni a otros mods. El mod ya
  escala así en el mundo (`MonitorRenderer`, `StatusScreen`).
- **Fuente:** para que no se vea irregular a escalas no enteras hay que dejar la bitmap de 8 px y usar un proveedor **`ttf`** con
  `size` y `oversample` (Inter, Geist, JetBrains Mono para código; revisar licencia).
- Esfuerzo: **M**. **Bloqueado por la pregunta Q2.**
- **(verificar)** que `enableScissor` y los tooltips respeten la escala en 26.3.

### B6. Catálogo de iconos
Tres formas, todas disponibles: **sprites** (ya existen, `chat/icon_*.png`), **glifos en una fuente propia** (lo mejor para
iconos dentro de texto: van en cualquier `Component` y toman el color del texto; el mod ya usa `Style.withFont`) y **dibujo por
código o texturas dinámicas** (por ejemplo un QR para el emparejado).
- Esfuerzo: **S** por tanda. Revisar licencias si se toman de T3 Code o de una librería.

### 4.1 Lo que sí limita (no es negociable)
- Todo cae dentro de la ventana del juego: una segunda ventana AWT choca con GLFW en macOS.
- La **accesibilidad hay que construirla**: los widgets propios no se narran ni se navegan con Tab como `Button`.
- Los campos de texto nuevos deben reutilizar la entrada por SDL3 del composer (`TextInputManager`).
- Las pantallas grandes con markdown deben cachear glifos (el chat ya lo hace con `DrawnRows`).

---

## 5. Frente C — Navegador para cosas de fuera (D2, D3)

### C1. Spike de medición (primero, antes de diseñar nada)
Medir **qué sitios de la lista de Facundo cargan y se loguean** en **Rinku** y en **MCEF Modern**: Gmail, Google Chat, GitHub,
documentación, dashboards. Nada de UI todavía: abrir una URL en una pantalla y anotar resultados.
- **El riesgo que hay que medir es concreto:** **Google bloquea el inicio de sesión en navegadores embebidos** ("This browser or
  app may not be secure"). Es una política deliberada contra webviews, reportadísima en apps de Electron y CEF. Lo más probable
  es que **Gmail y Google Chat no se puedan loguear** dentro del juego. GitHub y la mayoría de sitios sí funcionan.
- Esfuerzo: **S a M**. Necesita compilar y abrir el juego en el PC → lo pide el coordinador.
- **Bloquea C2.** Sin este dato, construir la pantalla es apostar.

### C2. `WebScreen` y lanzadores
Pantalla completa con CEF (dependencia **opcional**, `suggests`, como Mod Menu), abierta con una tecla o con **clic derecho en un
bloque o NPC** (D3). El mod ya funciona así: el clic en un monitor, un NPC o la estación de merge abre algo.
- Entrada: teclado, IME, portapapeles, rueda, descargas, selector de archivos. **M a L**.
- **Regalo de CEF:** acepta un factor de escala del dispositivo, así que "tamaño de texto independiente del GUI scale" sale casi
  gratis **en esta pantalla** (el ajuste de B5 pasa a ser el zoom de la página).
- **Decidir:** que las `WebScreen` **no pausen** el juego (`isPauseScreen=false`).

### C3. Seguridad y límites (no opcional)
- Cargar **solo una lista cerrada de dominios**; los enlaces fuera de ella se abren con `openUri` en el navegador del sistema.
- **No usar CEF para el login de T3 Connect ni de OAuth:** la práctica recomendada (RFC 8252) es el navegador del sistema, y
  muchos proveedores bloquean los webviews.
- El perfil de CEF (cookies) es un secreto: carpeta propia en `config/t3craft/`, permisos de propietario, nunca en el repo.
- **Nada de falsear el user agent** para saltar el bloqueo de Google: frágil y contra el espíritu de sus términos.
- Costes a asumir: descarga de Chromium en el primer arranque, varios procesos hijos y cientos de MB de RAM, sin H.264 en los
  builds estándar, y cada fork depende de un solo mantenedor (riesgo con la primera 26.4).
- **(verificar)** CEF en macOS: los forks declaran arm64, pero AWT y GLFW chocan históricamente ahí. Hay que probarlo en el Mac
  antes de contar con ello, sobre todo con D8 (jugar en el Mac).

### C4. Si Gmail queda fuera (depende de C1 y de la pregunta Q3)
Opciones, por orden de coste: aceptar que el navegador sirva para GitHub, docs y dashboards; o un **cliente nativo** contra la
API de Gmail o IMAP, mostrado en nuestra UI (una pantalla, o un bloque "bandeja" con avisos). Lo segundo es bastante más
trabajo y para Google Chat la API es más limitada.

---

## 6. Restricciones de ejecución

### 6.1 `AGENTS.md` queda desactualizado por D7
Su tabla "Where things run" dice que el PC corre **todos** los builds de Gradle y los renders, y que **solo el coordinador**
compila ahí, a través de `tools/heavy.sh`. Con D7 hay dos cosas nuevas:
- El cliente de desarrollo y su Gradle corren en **Windows**, lanzados por Facundo, no por el coordinador.
- `tools/heavy.sh` es un script de Linux con un lock en `/tmp`: un Gradle en Windows **no toma ese lock**, así que un build de
  Facundo y uno del coordinador en WSL podrían coincidir en la misma máquina.

**Resuelto (Q5 aprobada):** el **coordinador** añade a esa tabla que Windows corre el cliente de desarrollo interactivo y que
WSL se queda con el Gradle de los agentes y los renders, más una línea sobre el lock. Este hilo no toca `AGENTS.md`.
`docs/dev.md` ya documenta los dos lados de la PC y el aviso del lock.

### 6.2 Choque con la limpieza en curso (issues #3 a #8)
La limpieza mueve paquetes (#5) y toca duplicados y clases grandes (#6). **B1 y B4 tocan muchos archivos de `client/`**, así que
hacerlos antes de #5 garantiza conflictos.
- **Recomendación:** A (no toca código del mod) va ya; **B después de #5**, o aceptando el coste de rebase.
- C es más aislado (paquete nuevo), así que puede ir en paralelo.

### 6.3 Quién ejecuta qué
- Este hilo: **solo investigación y docs**.
- Compilar y abrir el juego: **solo el PC**, por el coordinador (o por Facundo en su cliente de desarrollo tras D7).
- Los hilos hijos solo escriben código y hacen push.
- `uiRender` se queda como comprobación de layout para agentes, no como herramienta de diseño: usa `Unsafe.allocateInstance`
  sobre `Minecraft`, pinta con Java2D y no tiene Unifont, shaders ni IME.

---

## 7. Orden propuesto

| Fase | Contenido | Esfuerzo | Bloqueada por |
|---|---|---|---|
| **0** | **A3** (documentar el ciclo en `dev.md`) y **A1** (arreglar el camino oficial, sin código) | S | Nada. **Se puede hacer ya** |
| **1** | **A4** (script del jar al Mac) y **C1** (spike de medición del navegador) | S a M | Fase 0; C1 necesita el PC |
| **2** | **B1** (tokens como recurso) y **B2** (galería `/t3dev ui`) | S a M | Q1; idealmente tras el issue #5 |
| **3** | **B3** (kit) y **B4** (migrar pantallas) | M a L | Fase 2, issue #5 |
| **4** | **B5** (UI scale y fuente) y **B6** (iconos) | M | Q2 |
| **5** | **C2** (`WebScreen` y lanzadores) y **C3** (seguridad) | M a L | C1 |
| **6** | **Frente D**, por valor: señales discretas (título de ventana y parpadeo con GLFW, action bar), cola de atención (tecla "siguiente que me necesita" y flecha al NPC), webhooks con ntfy y Discord, sonido propio, toasts con retrato | S cada una | Fase 0; algunas usan el kit |
| **7** | Pantalla de diff (nativa) y **frente E** (bloques útiles) | M a L | Ver 7.1 |

### 7.1 Una interacción que cambia prioridades
La pantalla de diff nativa era la idea nº 6 del mapa original. **Si el navegador cubre GitHub (D2), revisar PRs puede hacerse
ahí**, y el diff nativo baja de prioridad: pasaría a ser "lista de archivos y +N/−N en nuestra UI, y el diff completo en el
navegador". Conviene decidirlo **después** de C1.

---

## 8. Lo que no se hace, y por qué

| Descartado | Motivo |
|---|---|
| T3 web dentro del juego como plan principal (§10 de la investigación) | D2: el navegador es para cosas de fuera. Se conservan su inventario de rutas y el patrón de lanzadores |
| `fabric-hot-reload` y cualquier mod extra para el ciclo | D5. Además es inmaduro: una jornada de commits, sin estrellas, probado solo en Windows |
| Boss bar, scoreboard y lista Tab | La `StatusBar` propia ya lo cubre mejor; la boss bar exige mixin |
| Shaders y post-efectos | Chocan con Iris y Sodium y aportan poco sobre colores y partículas |
| Discord Rich Presence | Expone títulos de hilos y no aporta nada funcional |
| Calendario y escritura de tareas programadas | T3 ya tiene su UI; escribir no está aprobado |
| API local de estado en loopback | Casi todo lo que daría ya lo ofrece T3 a esas herramientas |
| Libros y mapas | Valor bajo frente al chat con markdown; solo si se quiere el efecto diegético |
| JavaFX o Swing para el navegador | Chocan con GLFW en macOS |
| Chromium headless propio | Duplica el preview de T3 y añade un proceso pesado |
| Falsear el user agent para Gmail | Frágil y contra el espíritu de los términos de Google |
| Pantallas de navegador en bloques como objetivo | D3: serían decorativas. Quedan para el frente E |

---

## 9. Preguntas que bloquean fases

| # | Pregunta | Bloquea | Estado |
|---|---|---|---|
| **Q1** | ¿Mover los tokens de UI a `ui-style.json` (cambio en `ChatTheme` y sus consumidores) para que F3+T recargue colores y medidas? | Fase 2 | **Aprobada (2026-11-09), a hacer después del issue #5** |
| **Q2** | ¿El UI scale propio **reemplaza** al GUI scale en nuestras pantallas, o es un **multiplicador** encima de él? Lo segundo es más seguro para quien ya tiene el suyo afinado | Fase 4 | Abierta |
| **Q3** | Gmail y Google Chat: ¿se acepta que probablemente no se puedan loguear y que el navegador sirva para GitHub, docs y dashboards? ¿O se explora el cliente nativo de correo? | C4 | Abierta, se decide con el resultado de C1 |
| **Q4** | ¿Se autoriza el **spike C1** (compilar y abrir el juego en la PC para medir qué sitios funcionan)? | Fase 1 | Abierta |
| **Q5** | ¿Actualizar `AGENTS.md` con D7 y la nota del lock (6.1)? | 6.1 | **Aprobada (2026-11-09): lo hace el coordinador** |
| **Q6** | ¿Qué pantallas se pulen primero: las de widgets vanilla (ajustes, emparejado, confirmaciones) o el chat? | Fase 3 | **Resuelta (2026-11-09): primero las de widgets vanilla.** Facundo vio las capturas de `uiRender` y pidió adaptar las demás interfaces al estilo del chat |
| **Q7** | ¿Se aprueba un "runner de escenarios de UI" con los client gametests (capturas reales, repetibles) a pesar de la regla "sin tests"? Sería una herramienta, no una suite | Opcional en fase 2 | Abierta |
| **Q8** | ¿Cuáles de estas ideas se convierten en **issues**? | Todo el seguimiento | **Resuelta: los 10 de §10, creados el 2026-11-09** |

### Preguntas abiertas de menor urgencia (del mapa original)
- **Scopes:** costo y tokens piden `diagnostics:read`, el navegador interactivo de T3 pide `preview:operate`, y comentar PRs
  pide `source-control:write`. Hoy el mod **rechaza** cualquier scope fuera de `read` + `operate`. ¿Se mantiene la regla?
- **Datos fuera de la máquina:** ¿puede el mod mandar títulos de hilos a ntfy.sh o a Discord, o solo "hay algo pendiente"?
- **Avisos con el juego cerrado:** las integraciones salientes del mod solo funcionan con Minecraft abierto. Si los quieres sin
  el juego, hace falta un proceso aparte (ligado al issue #1).
- **Frente E:** ¿un bloque por hilo, o una pared compartida? ¿Y en qué mundo juegas (singleplayer, LAN o servidor remoto)? Los
  bloques solo existen donde el mod está en el servidor.

---

## 10. Issues (creados el 2026-11-09, etiqueta `enhancement`)

| Issue | Tema | Frente | Fase | Dependencias |
|---|---|---|---|---|
| [#11](https://github.com/Facundo-Barbera/t3craft/issues/11) | Ciclo de desarrollo de UI: hot reload oficial y ver el resultado con fidelidad | A (A1, A4) | 0 y 1 | Ninguna. A3 ya hecho en `docs/dev.md` |
| [#12](https://github.com/Facundo-Barbera/t3craft/issues/12) | Tokens de UI como recurso recargable con F3+T | B1 | 2 | **Después del #5.** Q1 aprobada |
| [#13](https://github.com/Facundo-Barbera/t3craft/issues/13) | Galería de pantallas `/t3dev ui <escenario>` | B2 | 2 | Conviene tras el frente A |
| [#14](https://github.com/Facundo-Barbera/t3craft/issues/14) | Kit de UI propio y migración de las pantallas con widgets vanilla | B3 y B4 | 3 | **Después del #5** y del #12. Q6 resuelta. **Hecho en `feat/ui-native-kit`** (`docs/features/ui-kit.md`), pendiente de compilar y revisar en la PC |
| [#15](https://github.com/Facundo-Barbera/t3craft/issues/15) | Escala de UI propia, fuente TTF y catálogo de iconos | B5 y B6 | 4 | Tras el #14. **Q2 abierta** |
| [#16](https://github.com/Facundo-Barbera/t3craft/issues/16) | Spike: medir qué sitios funcionan en un navegador embebido (CEF) | C1 | 1 | Necesita compilar y abrir el juego en la PC. **Q4 abierta** |
| [#17](https://github.com/Facundo-Barbera/t3craft/issues/17) | Navegador en el juego para cosas de fuera: pantalla, lanzadores y dominios | C2 y C3 | 5 | Tras el #16. Q3 se decide con su informe |
| [#18](https://github.com/Facundo-Barbera/t3craft/issues/18) | Señales discretas de atención: título de ventana, atención del sistema y barra de acción | D | 6 | Ninguna fuerte |
| [#19](https://github.com/Facundo-Barbera/t3craft/issues/19) | Cola de atención: tecla al siguiente hilo que te necesita e indicador direccional | D | 6 | Ninguna fuerte |
| [#20](https://github.com/Facundo-Barbera/t3craft/issues/20) | Eventos salientes: webhook genérico con plantillas de push y chat | D | 6 | Ninguna fuerte |

Los cuerpos se escribieron sin datos de infraestructura (sin IPs, nombres de máquina, usuarios ni rutas personales) porque el
repo se va a publicar y los issues se transfieren al repo nuevo. Por eso no llevan enlaces a archivos: citan la ruta del plan y
su sección, que sobreviven a la transferencia.

El diff nativo (idea nº 6 del mapa original) **no tiene issue todavía**: su prioridad depende del #16, ver 7.1.
