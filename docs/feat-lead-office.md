# feat/lead-office: oficina del líder

Idea de Facundo: un hilo con edad que reparte mucho trabajo (subagentes o tareas delegadas) es un
**líder** o coordinador. Recibe su propia oficina en el HQ y desde ahí "dirige" a los demás.

## Detección (`dev.t3craft.hq.lead`)

| Clase | Rol |
|---|---|
| `t3.LeadSignals` | Interfaz mínima de linaje de un entorno: `parents()`, `createdAt`, `isChild`, `children` (más `recentChildren`, `workingChildren` y `liveChildren` por defecto). `LeadSignals.of(shells)` la calcula en una pasada con `parentThreadId`. |
| `LeadPolicy` | Umbrales (config `leadOffice`, aditiva) y la instancia compartida `active()`. |
| `LeadDetector` | Regla, histéresis, tope de oficinas y slots estables. Tiene estado; lo llama el cliente con throttle. |
| `LeadRoster` | Resultado inmutable: líderes (slot, hijos, "coordinando N") y `briefing` (hijo nuevo → líder). |
| `LeadOffices` | Roster compartido (volátil, con `version()`) y la convención `lead:<npcId>`. |

**Regla.** Un hilo de nivel superior (no hijo) que tiene escritorio en el HQ es líder si:
- tiene al menos `minAgeHours` (6), y
- creó al menos `recentChildren` (3) hijos en `recentWindowHours` (24), contando delegados y
  subagentes pero no forks, **o** tiene al menos `workingChildren` (2) hijos trabajando ahora.

**Histéresis.** Conserva la oficina mientras cumple la regla, durante `holdMinutes` (45) desde la
última vez que la cumplió, y nunca menos de `minHoldMinutes` (10) desde que la obtuvo. Un hilo más
activo nunca desplaza a un líder: las oficinas libres van a los candidatos con más actividad
(`3·trabajando + 2·recientes + vivos`, desempate por id). Hay como mucho `maxOffices` (3, tope 4).

```json
"leadOffice": { "enabled": true, "minAgeHours": 6, "recentChildren": 3, "recentWindowHours": 24,
  "workingChildren": 2, "holdMinutes": 45, "minHoldMinutes": 10, "maxOffices": 3, "briefing": true,
  "childrenWaitForReview": true, "childrenNearLead": true }
```

## La oficina en el HQ: genérica para todos los layouts

`HqConstructionClient.liveRoster` (y `DemoHq.fromNpcs`) ponen al líder en un **proyecto propio**,
`lead:<npcId>`, con la etiqueta `★ <proyecto>`. Así no hace falta cambiar ningún allocator ni
generador:

- todos los layouts le dan un módulo propio (ala, barrio, hall o piso) y lo mantienen en el mismo
  índice mientras sea líder (regla "modules kept"), así que **la oficina no salta**;
- su ancla `desk_<npcId>` queda en ese módulo, así que su NPC camina hasta ahí y trabaja ahí;
- `HqInput` cambia cuando cambia el roster de líderes, y `HqConstruction` vuelve a planificar con su
  debounce y su cálculo asíncrono de siempre (nada en el render thread; respeta el gate de presencia);
- al dejar de ser líder vuelve a su proyecto, al primer escritorio libre.

`HqLayout.Module.type()` devuelve `LEAD_OFFICE` para esos módulos (y `leadNpcId()` da el líder).

- **Classic** (`LeadOfficeRoom`): el ala se dibuja como oficina. En el slot 0 está su escritorio
  normal. En el slot 1, sillas de visita frente al escritorio y el estandarte del entorno. En el
  segmento 1, librero y pizarra. En el segmento 2, libreros y sillas de reunión que miran a la big
  screen del fondo.
- **Fallback para el resto** (`LeadOfficeDecor`, post-proceso del plan, independiente del layout):
  escritorio ampliado, con una pantalla extra de 2×2 sobre una base junto a la del escritorio y un
  estandarte al lado del puesto. Solo toca celdas que el plan lista como aire y que no son anclas.
  Si no hay sitio, no hace nada.
- **Big screen:** el binding `project:lead:<npcId>` (la big screen del módulo y la pantalla extra)
  muestra al líder y a sus hijos vivos con su estado (`client.blocks.LeadFeed`). Se memoiza con la
  versión del roster.

### Ciudadela: la oficina (struct/lead-office)

La pieza está en `citadel/CitadelLeadOffice.java` y la llama `CitadelBarrios.hall`. Todo sale de
`r.w()`, `r.d()` y `fh()` (ninguna medida fija), así que sirve a cualquier escala de la ciudadela:

```java
boolean leadHall = CitadelLeadOffice.isLead(ground);              // módulo de planta baja de un líder
int F = upper != null || leadHall && CitadelLeadOffice.twoFloors(r) ? 2 : 1;
...
if (leadHall) { if (F == 2) CitadelLeadOffice.flight(c, r, style); }   // sin mobiliario genérico
else { hallStairs / HallInterior.furnish }
...
if (!CitadelLeadOffice.place(c, mod, r, f, style, mats)) { /* los 6 escritorios de siempre */ }
```

`place` devuelve `false` y no pinta nada si el módulo no es una oficina de líder en planta baja.
`CitadelBarrios.deskAt` es el cuerpo de `desk` con un `DeskSpot` arbitrario.

**Sala grande** (`w >= 13`; escala 200, por defecto: 17×20, 7 de alto): una planta con todo.
- Pasillo central de 5 con alfombra, que lleva de la puerta al escritorio del líder (3 de ancho, monitor
  3×2); el líder está de pie en el pasillo, mirando al fondo, como los demás escritorios.
- Detrás, en la pared del fondo, la big screen `bigscreen_lead@<i>@11x4=project:lead:<npcId>` sobre un mueble
  de cofres y barriles, entre dos mástiles.
- Sala de espera: 3 filas de 6 sillas a cada lado del pasillo separadas 3 bloques, mirando al líder y a la
  pantalla (`leadseat_<npcId>#k`: la celda libre delante de cada silla, las más cercanas primero, 18). Salón
  con mesa baja y 8 asientos (oeste), estantes de archivo en el vestíbulo.
- Pizarra de notas de 8×4 en la pared oeste (los cristales de detrás pasan a muro).
- Rincón de reuniones (este): mesa de 4×2 con 12 sillas en total, alfombra y pizarra de presentación en el
  muro frontal; mostrador de utilidades en la pared este (mesa de trabajo, cofre, barril, atril, cartografía).
- Vigas de techo cada 5 filas con linternas colgantes; suelo de piedra en el vestíbulo y bajo el escritorio.
- Asta con bandera del color del entorno en la cumbrera (fuera de la chimenea).

**Sala pequeña** (`w < 13`, escala 100: 9×10): usa **las dos plantas** del salón (el líder no comparte su
barrio): abajo la sala de briefing (mismo programa con pantalla 7×3, pizarra 4×3, dos filas de 4 sillas) y
una escalera a lo largo del muro este (`flight`: primer escalón de frente, 2 libres encima); arriba la sala
de planificación (mesa de 4×2 con 8 sillas, pizarra de presentación, mostrador y estantes).

Esa clave `lead@<i>` desactiva el fallback para ese módulo.

## "Dirigir"

- El NPC del líder trabaja en su oficina (su ancla de escritorio está ahí).
- **Instrucciones** (`npc.behavior.LeadBriefing`): un hijo delegado creado hace menos de 2 min
  (`LeadRoster.briefing`) recibe como destino un punto detrás de su líder, mirando hacia él y su
  pantalla. Se queda ahí hasta llevar 4 s parado (o un máximo de 30 s) y luego va a su escritorio.
  Solo cambia el ancla que el director le pasa al cerebro, así que usa las mismas rutas asíncronas y
  el mismo presupuesto. Un NPC recién creado aparece directamente en la oficina. Solo aplica a hijos
  con NPC propio (los subagentes ya siguen a su padre como ayudantes).
- **Nameplate:** el panel del NPC líder suma `★ Coordinando N agentes` (`t3craft.lead.coordinating`,
  N = hijos trabajando, o vivos si no hay ninguno trabajando), delante de las líneas de ayudantes y
  de la cola.

## Rendimiento

`LeadOfficeClient.refresh` corre en el refresh directo (hilo del cliente, no en el render) como mucho
cada 2 s: una pasada por los shells de cada entorno. El replan del HQ es el de siempre: debounce y
cálculo asíncrono (`HqTarget` en `Offload`). La pantalla del líder se memoiza.

## Conectarlo a `Lineage` (feat/thread-lineage)

`LeadDetector` solo ve `LeadSignals`. Cuando llegue `dev.t3craft.t3.Lineage`:

1. Que `Lineage` implemente `LeadSignals`, o que un adaptador `LeadSignals.of(Lineage)` lo haga:
   - `parents()`: los hilos con hijos del índice;
   - `children(id)`: los hijos directos, con `Child.delegated = isDelegated() || relationship == "subagent"`
     (o la definición que prefiera Facundo);
   - `workingChildren(id)`: los descendientes vivos trabajando, si se quiere contar a los nietos.
2. En `LeadOfficeClient.refresh`, cambiar `LeadSignals.of(view.threads())` por el `Lineage` del
   entorno.

No hay que tocar nada más.

## Revisión: los hijos terminados esperan al líder (feat/lead-review-walk)

Idea de Facundo: quien revisa el trabajo de una tarea delegada es el líder, no el jugador.

**Quién espera.** Un hijo (subagente o tarea delegada) con NPC propio, vivo (no archivado, settled ni
borrado), cuyo padre tiene escritorio, espera revisión cuando está `done`, o `idle` tras completar un
turno (`latestRunCompletedAt`). Lo calcula `LeadOfficeClient.reviews` en el mismo refresh con throttle
(una pasada por los shells) y lo publica en `LeadRoster.reviews()` (hijo → `Review(padre, título,
inOffice)`).

**Dónde espera** (`npc.behavior.ReviewWalk`):
- Si el padre tiene oficina de líder: en las sillas de visita de la oficina, sentado y mirando al
  líder; si no alcanzan, en puntos frente a la puerta de la oficina; si tampoco, en filas detrás del
  escritorio del líder (`AnchorSource.reviewSeats`).
  - Las sillas son anclas nuevas `leadseat_<npcId>#k` (`AnchorNames.leadSeat`): la celda libre frente a
    cada silla, como los bancos del hub. Las publican `CitadelLeadOffice` (la sala de espera: 8 sillas a 9×10, 18 a 17×20) y la oficina classic (`LeadOfficeRoom.seats`, las dos sillas de visita del slot 1). No son
    anclas "standing", así que los validadores del plan las ignoran.
  - La puerta es el ancla `module@<i>` del módulo del escritorio del líder: tres puntos dos bloques
    fuera de la puerta, mirando hacia dentro (`HqWorldAnchors.reviewSeats`, cacheado por instancia).
  - Con el fallback (`LeadOfficeDecor`, y la ciudadela mientras `CitadelLeadOffice` no esté enganchada)
    no hay sillas: se usan la puerta y las filas.
- Si el padre no tiene oficina (no es líder o no cupo): filas detrás del escritorio del padre (a 1,8
  bloques y luego cada 1,3; centro, derecha, izquierda), mirándolo.
- Varios hijos del mismo padre forman una **fila** en orden de llegada: el primero toma el mejor sitio
  y, cuando uno se va, los demás avanzan.

**Comportamiento.** La espera se le pasa al cerebro como un paseo (`NpcBrain.stroll`: ir, quedarse de
pie o sentado, mirar), así que no hay estado nuevo en el cerebro. Un NPC que acaba de terminar celebra
y luego camina. Si un sitio resulta inalcanzable, se descarta durante 60 s y se le da otro. Mientras
espera no pasea (`IdleWander`).
- Vuelve a trabajar: el modo WORKING ignora el paseo y el NPC vuelve a su escritorio.
- Pasa a settled o archivado, o el líder lo descarta: sale de la lista y se va como cualquier hilo
  (`Departure`).

**Sin aviso para el jugador.** Un hijo que reporta a su padre no dispara el aviso "Thread finished"
(chat, toast y sonido) al terminar (`LeadOfficeClient.reportsToParent`, filtrado en
`T3CraftClient.refreshDirect`). Los estados que sí necesitan al jugador (needs input, error) avisan
igual. El panel del NPC muestra `⌛ Esperando revisión de <padre>` (`t3craft.lead.awaitingReview`).

**Config.** `leadOffice.childrenWaitForReview` (true por defecto). Con false, todo lo anterior queda
apagado y los hijos se comportan como antes.

### Escritorios de los hijos cerca de la oficina

`dev.t3craft.hq.lead.LeadTeamSeats`, aplicado en `HqTarget.compute` después del allocator de cualquier
layout (no se tocó ningún allocator):
- Solo se mueve un hijo de líder cuyo asiento es **nuevo** en esta asignación. Los asientos que ya
  existían nunca se mueven, así que nada salta en un replan. El resultado pasa a ser el `previous` de la
  siguiente asignación, y los allocators lo conservan.
- Se mueve solo entre módulos de **su propio proyecto**, al módulo cuyos escritorios están más cerca
  del escritorio del líder (la oficina), si queda al menos 1 bloque más cerca. Puede ir a un slot libre
  o intercambiarse con otro hilo que también sea nuevo y no sea hijo de líder. Nadie que ya tenía
  escritorio cambia de sitio.
- Las distancias salen de las anclas `desk_<id>` del plan del layout asignado, así que valen las
  posiciones reales de cada estilo: mismo hall o ala primero, luego el más cercano. El plan se genera
  dos veces solo cuando hay un movimiento (en el cálculo asíncrono de siempre).
- No crea índices de módulo. Un módulo que queda vacío (solo puede ser uno creado en esta misma
  asignación) se elimina. El formato guardado no cambia.
- Config: `leadOffice.childrenNearLead` (true por defecto).

**Límite.** El hijo comparte el proyecto del padre. Si todos los módulos del proyecto están llenos, el
anexo nuevo va donde lo ponga el allocator de cada layout. En la ciudadela, en el anillo y en el
ensanche, eso es junto a los módulos del proyecto, no necesariamente junto a la oficina. El roster de
líderes llega por `LeadOffices.current()` y el detector corre cada 2 s, así que un hijo que entra justo
antes de que el detector lo vea se sienta donde diga el allocator.

## Rendimiento (revisión)

`ReviewWalk` reasigna sitios como mucho cada 20 ticks, o cuando cambian el roster o la base. Por NPC
solo hace una lectura de mapa. El paseo usa la `Navigation` compartida del director (caché de rutas y
presupuesto de nodos por tick, responde PENDING mientras busca). Nada corre en el render thread. Step de
PerfStats: `npc.review`.

## Límites conocidos

- Modo bridge: sin linaje, así que no hay líderes.
- Si el HQ está lleno (`MAX_MODULES`), un líder puede quedarse sin escritorio igual que cualquier
  hilo.
- Si el escritorio del líder aún no está construido, el punto de instrucciones cae en el lobby.
