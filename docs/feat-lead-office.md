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
  "workingChildren": 2, "holdMinutes": 45, "minHoldMinutes": 10, "maxOffices": 3, "briefing": true }
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

### Ciudadela: cómo engancharla (coordinador)

La pieza está en `citadel/CitadelLeadOffice.java`. No se tocó ningún archivo existente de la
ciudadela. En `CitadelBarrios.hall`, dentro de `for (HqLayout.Module mod : ...)`, envuelve el bucle
de escritorios:

```java
if (!CitadelLeadOffice.place(c, mod, r, f)) {
    for (int slot = 0; slot < mod.capacity(); slot++) {
        String thread = mod.threadAt(slot);
        if (thread != null) {
            desk(c, mod.index(), r, f, slot, thread);
        }
    }
}
```

`place` devuelve `false` y no pinta nada si el módulo no es una oficina de líder. La pieza pinta:
- el escritorio del líder (slot 0);
- una big screen de 3×2 en el slot 1 (`bigscreen_lead@<i>@3x2=project:lead:<npcId>`);
- el estandarte;
- librero (slot 2) y pizarra (slot 3);
- sillas que miran al fondo (slots 4 y 5).

Esa clave `lead@<i>` desactiva el fallback para ese módulo. Mientras no se enganche, la ciudadela usa
el fallback.

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

## Límites conocidos

- Modo bridge: sin linaje, así que no hay líderes.
- Si el HQ está lleno (`MAX_MODULES`), un líder puede quedarse sin escritorio igual que cualquier
  hilo.
- Si el escritorio del líder aún no está construido, el punto de instrucciones cae en el lobby.
