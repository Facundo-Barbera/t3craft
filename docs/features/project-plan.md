# Plan del proyecto (oficina integrada, primer corte)

Issue #51. El plan existe como objeto dentro del juego: cada tarea dice qué hay que hacer, de qué depende, qué decisión
espera de Facundo, qué hilo la hace y en qué punto de la revisión está. El dueño del plan es el juego: un único
almacén en el directorio de config del mod, que todas las superficies leen y escriben.

Este corte implementa un solo recorrido de punta a punta: **preparar → vincular a un hilo → revisión → decidir**.

## Recorrido

1. **Preparar** una tarea: título, resultado esperado, dependencias (otras tareas), decisión pendiente (opcional) y
   notas. Queda como **borrador** en la vista Mesa.
2. **Vincularla a un hilo real**, desde la tarea:
   - **Lanzar hilo**: entorno, proyecto, espacio de trabajo (Local o New worktree con rama base) y el mensaje inicial,
     que se compone a partir de la tarea y se puede editar. Usa el mismo `orchestration.launchThread` que el lanzador
     de borradores de la pantalla T3 (`NewThreadForm`, modelo por defecto del proyecto), por `OperateUi.run` /
     `OperateGate`, y pide confirmación antes de enviar.
   - **Enlazar hilo**: entorno + hilo existente (del shell que el cliente ya tiene). No envía nada. Por defecto solo
     cuenta el **próximo** turno que termine el hilo; la casilla «Este hilo ya entregó la tarea» hace que un hilo ya
     terminado pase a revisión en el acto.

   La tarea pasa a **En curso**, con el estado real del hilo y «Abrir hilo».
3. **Revisión**: cuando el hilo vinculado termina su turno, la tarea pasa a **En revisión** con tres marcas separadas,
   que nunca se deducen una de otra:
   - **Implementado**: derivado del hilo (terminó su turno o fue settled después de la vinculación o de la última
     devolución). Ver `PlanReview`.
   - **Comprobado por el agente**: texto libre con lo que el agente declara haber comprobado. Vacío = «nada
     declarado»; nunca se asume que compiló. Lo escribe Facundo (p. ej. copiándolo del hilo) y se guarda con «Guardar
     lo declarado».
   - **Probado por ti**: solo la casilla de Facundo lo cambia.
4. **Decidir**:
   - **Aceptar**: la tarea queda aceptada. No lanza la siguiente tarea, no envía nada y no autoriza otra fase.
   - **Devolver observaciones**: un único mensaje al hilo vinculado (`ChatSender.send`, `message.dispatch`, por
     `OperateGate`), precedido de una línea «Observaciones sobre la tarea del plan «…»:». Cuando T3 lo acepta, la tarea
     vuelve a **En curso** y solo el siguiente turno terminado del hilo la trae de nuevo a revisión.

Otras acciones locales (no tocan T3): aceptar una propuesta, eliminar un borrador o una propuesta, desvincular (la
tarea vuelve a borrador; el hilo no se interrumpe ni recibe nada), descartar el aviso de una propuesta en conflicto.

## Reglas de comportamiento

- Abrir la página, una tarea o su hilo **no envía nada ni dispara ningún agente**. Las únicas escrituras en T3 son
  lanzar el hilo y devolver observaciones, ambas con un botón explícito (el lanzamiento además con confirmación).
- **Sin vigilancia nueva**: el plan no se suscribe a nada ni hace peticiones. Mientras la página está abierta relee, una
  vez por segundo, las filas del shell que el cliente ya tiene en memoria (como `QueuePanel`), y al abrirse comprueba
  si algún hilo vinculado terminó mientras estaba cerrada. No hay temporizadores ni polling fuera de la página.
- Una propuesta nueva o reimportada **no invalida lo aceptado**: nunca sobrescribe tareas del plan, tareas editadas ni
  marcas del usuario (ver «Propuestas»).
- Aceptar una entrega **no lanza** la siguiente tarea.
- **Sin estados simulados ni datos de ejemplo**: el plan vacío muestra una frase de cómo crear la primera tarea.

## Superficies

Todo se hace en interfaces (Facundo, 2026-11-10: nada de comandos de línea para operar). Una sola página del kit,
**Plan** (`PlanPanel`, en `PlanScreen` sobre `T3PanelScreen`), con tres vistas en pestañas y, arriba, lo siguiente que
necesita la atención de Facundo (`PlanAttention`: entrega por revisar → hilo vinculado que te necesita o falló →
decisión pendiente → propuesta sin aceptar), que abre la tarea al pulsarlo.

| Vista | Qué muestra |
|---|---|
| **Mesa** | Propuestas sin aceptar (no cuentan como plan) y borradores: resultado, dependencias, decisión pendiente. «Lanzar hilo», «Enlazar hilo», «Aceptar propuesta». |
| **En curso** | Tareas vinculadas, con el estado real del hilo y «Abrir hilo». |
| **Revisión** | Entregas con sus tres marcas, «Aceptar» / «Devolver observaciones»; debajo, las aceptadas. |

La lista muestra resultado, dependencias y decisión; las notas, el formulario de edición, el hilo y las marcas
editables están en la página de la tarea (`PlanTaskPanel`). Abajo de la página: «Nueva tarea» e «Importar
propuestas».

### Accesos

- **Comando** `/t3craft plan`, sin argumentos: solo abre la página (igual que `/t3craft queue`). **No hay
  subcomandos**: crear, editar, vincular, importar, marcar, aceptar y devolver se hacen solo desde la página.
- **Tecla** «Abrir el plan» (`key.t3craft.open_plan`, Opciones › Controles › T3Craft), **sin asignar** por defecto, como
  las teclas de la cola de atención. Abre la Mesa cuando no hay otra pantalla abierta.
- **Podios de decisión** (oficina): sin cambios cuando un hilo espera (abren ese hilo). Cuando nadie espera, además de
  su línea habitual (o en silencio, según su opción `nobody`), abren la **Mesa** del plan.
- **Merge station**: su lista de pull requests gana un botón «Revisión del plan» abajo que abre la vista **Revisión**
  encima (Esc vuelve a la lista). Se añade a todas las merge stations del HQ, también a `mergestation_office`; la
  galería no cambia. Agachar + clic con la mano vacía sigue abriendo la página de config del bloque (#43): por eso la
  entrada no usa ese gesto.
- **Panel derecho de la pantalla T3**: la página está lista pero **no registrada** en este corte (otro hilo trabaja en
  `RightPanels`/`T3RightPanel`). Línea para registrarla:
  `RightPanels.registerIfAbsent("plan", "Plan", dev.t3craft.client.plan.PlanPanel::create);`

Las estaciones de trabajo no muestran todavía el título de la tarea (queda para el siguiente corte).

## Almacén

`config/t3craft/plan.json` (`ModConfig.dataDir()`), una sola instancia (`PlanClient.store()`, `PlanStore`):

- se lee en el primer uso y se escribe en cada cambio de forma atómica (fichero temporal + move);
- `version()` crece en cada cambio; las páginas relayan solo cuando se mueve;
- si el fichero no se puede leer, se copia aparte (`plan.json.unreadable-<millis>`) antes de escribir nada y la página
  lo dice;
- formato versionado y **aditivo** (`PlanCodec`): las claves desconocidas, de arriba o de cada tarea, se conservan al
  escribir.

```json
{"version": 1, "tasks": [{
  "id": "t-1a2b3c4d", "title": "…", "outcome": "…", "dependsOn": ["…"], "decision": "", "notes": "",
  "stage": "draft|proposed|in_progress|in_review|accepted", "origin": "local|proposal",
  "source": {"envId": "…", "threadId": "…"},
  "link": {"envId": "…", "threadId": "…", "launched": true, "linkedAt": 0, "baseline": 0},
  "agentChecked": "", "userTested": false, "feedback": "…", "edited": false,
  "incoming": {"title": "…", "outcome": "…", "decision": "…", "from": {"envId": "…", "threadId": "…"}, "at": 0},
  "createdAt": 0, "updatedAt": 0}]}
```

`baseline` es la marca de finalización del hilo (`max(latestRunCompletedAt, settledAt)`, reloj de T3) en el momento de
vincular o devolver; solo un turno terminado después cuenta como entrega. Si el hilo no está visible al devolver, se usa
el reloj local (puede haber un desfase pequeño).

## Propuestas del coordinador

Un hilo coordinador propone tareas escribiendo en un mensaje suyo un bloque cercado `t3craft-plan` con JSON:

````
```t3craft-plan
{"version": 1, "tasks": [
  {"id": "plan-page", "title": "Página del plan", "outcome": "La página lista las tareas",
   "dependsOn": [], "decision": "", "notes": ""}
]}
```
````

- `id` (obligatorio, `[A-Za-z0-9._-]`, hasta 64) y `title` (obligatorio, hasta 120) ; `outcome`, `dependsOn` (ids),
  `decision` y `notes` son opcionales (hasta 4000 caracteres). También vale un array de tareas sin envoltorio. Máximo 50
  tareas por mensaje. Ver `PlanProposals`.
- **Importar propuestas** (botón de la página) es una acción explícita: el usuario elige entorno e hilo y se lee el
  mensaje **más reciente** con al menos un bloque entre los mensajes que el juego ya tiene de ese hilo (la caché de
  transcripciones del chat y el último mensaje del shell). No se descarga nada: si el bloque no está, la página pide
  abrir el hilo con «Abrir hilo» (que carga su historial en el chat) y volver a importar.
- Las tareas entran como **propuesta** y no cuentan como plan hasta que Facundo las acepta.
- Reimportar: un id nuevo entra como propuesta; un id que ya existe solo se actualiza si sigue siendo una propuesta
  pendiente, sin editar y del mismo hilo. En cualquier otro caso **gana lo local**: la tarea no cambia y, si la
  propuesta es distinta, la tarea muestra un aviso con el título y el resultado propuestos («Descartar aviso» lo
  quita). Nunca se borra nada por faltar en una propuesta nueva.

## Código

| Clase | Qué hace |
|---|---|
| `dev.t3craft.plan.PlanTask` | La tarea (record), etapas y referencias a hilos. |
| `dev.t3craft.plan.PlanStore` | El almacén único: lectura, escritura atómica, transiciones, importación, `syncReview`. |
| `dev.t3craft.plan.PlanCodec` | JSON versionado y aditivo. |
| `dev.t3craft.plan.PlanProposals` | Bloques `t3craft-plan`. |
| `dev.t3craft.plan.PlanReview` | Cuándo un hilo «terminó su turno». |
| `dev.t3craft.plan.PlanAttention` | Lo siguiente que necesita a Facundo. |
| `dev.t3craft.client.plan.PlanClient` | Almacén del cliente, `/t3craft plan`, tecla, acceso al shell. |
| `dev.t3craft.client.plan.PlanPanel` / `PlanScreen` | La página y su pantalla. |
| `dev.t3craft.client.plan.PlanTaskPanel` | Una tarea: edición, vinculación, marcas, decisión. |
| `dev.t3craft.client.plan.PlanLaunchPanel` | Lanzar un borrador como hilo nuevo. |
| `dev.t3craft.client.plan.PlanThreadPanel` | Elegir hilo para enlazar o para importar propuestas. |
| `dev.t3craft.client.plan.PlanActions` | Aceptar, devolver observaciones, abrir hilo. |

## Decisiones por defecto (conservadoras)

- La transición a revisión se guarda en el fichero cuando la página la observa (no se recalcula cada vez), para que un
  hilo archivado después no devuelva la tarea a «En curso».
- Un hilo vinculado que **falla** no cuenta como implementado (sale arriba como «el hilo falló»); uno settled sí.
- «Comprobado por el agente» no se rellena solo: lo escribe Facundo. Devolver observaciones no borra las marcas.
- Lanzar usa el modelo por defecto del proyecto (sin selector de modelo en este corte) y `startFromOrigin` falso.
- No hay comprobación de ciclos en las dependencias; solo se impide depender de sí misma.
- Las dependencias no bloquean nada: son información.

## Ampliación propuesta (no implementada)

- **Mesa de proyecto** en la oficina: un bloque o estructura donde se ve la Mesa en el mundo (borradores, decisiones
  pendientes) y desde donde se abre la página.
- **Puestos**: las estaciones de trabajo de un hilo vinculado muestran el título y la etapa de su tarea.
- **Espacio de revisión**: un rincón de la oficina para las entregas en revisión, con las tres marcas a la vista.
- **Pausar** una tarea = interrumpir el hilo conservando la tarea y su vínculo (con la confirmación de las acciones
  destructivas).
- **Aceptación por partes**: aceptar una entrega parcial y devolver el resto en el mismo paso.
- Que el agente declare lo comprobado en un bloque propio (p. ej. `t3craft-check`) que la página pueda importar a la
  marca «Comprobado por el agente», siempre como acción explícita.

## Probarlo

1. `/t3craft plan` (o asigna la tecla «Abrir el plan»). Con el plan vacío se ve la frase de cómo empezar.
2. «Nueva tarea» → título, resultado, decisión → «Crear». Aparece en la Mesa y arriba como «Decisión pendiente».
3. «Abrir» → «Lanzar hilo» → elige entorno (con control) y proyecto → «Lanzar» → confirma. La tarea pasa a En curso.
4. Cuando el hilo termina su turno (con la página abierta o al volver a abrirla), la tarea aparece en Revisión con
   «Implementado: sí», «Comprobado por el agente: nada declarado», «Probado por ti: no».
5. En la tarea: escribe lo que declara el agente → «Guardar lo declarado»; marca «Lo he probado yo».
6. «Devolver observaciones» → texto → «Enviar al hilo»: llega un mensaje al hilo y la tarea vuelve a En curso; al
   terminar el nuevo turno vuelve a Revisión. O «Aceptar»: queda en Aceptadas y no se lanza nada.
7. Propuestas: en un hilo, que el agente escriba un bloque `t3craft-plan`; abre ese hilo en el chat; en la página,
   «Importar propuestas» → entorno + hilo → «Importar». Las tareas salen en Mesa como propuesta; acéptalas.
   Reimporta con un título cambiado: una propuesta sin tocar se actualiza; una aceptada muestra el aviso y no cambia.
8. En la oficina: un podio sin nadie esperando abre la Mesa; la merge station → «Revisión del plan» abre la Revisión.
