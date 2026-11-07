# feat/survival: T3Craft en modo supervivencia

Rama `feat/survival`, basada en `origin/main@f6364f7`.
Paquetes propios:
- `dev.t3craft.survival` (main, común)
- `dev.t3craft.survival.client` (client)

Tests: `mod/src/test/java/dev/t3craft/survival/` (23 tests JUnit).

## Resumen

- El **T3 Terminal** se craftea en supervivencia y la receta aparece en el libro en cuanto se recoge
  cualquiera de sus ingredientes.
- Al usarlo por primera vez se otorga el advancement **"Primer contacto"**.
- Hay una **política de supervivencia** (`SurvivalRules`), configurable en `config/t3craft.json`, para
  que builders, site y portable la consulten. **Hoy nadie la consulta todavía**: los puntos de
  integración están más abajo y no se tocó ningún paquete ajeno.
- `/t3craft survival` muestra qué decide la política para el jugador en ese momento.
  `/t3craft survival reload` relee la config.

## 1. Receta y desbloqueo

**Receta** (`data/t3craft/recipe/t3_terminal.json`, sin cambios respecto a main):

| | |
|---|---|
| Tipo | `minecraft:crafting_shapeless`, categoría `equipment` |
| Ingredientes | panel de vidrio + redstone + lingote de cobre |
| Resultado | 1 × T3 Terminal |

**Por qué se mantiene así:**
- El terminal es solo una UI de lectura y no da ventaja de juego. No tiene sentido que cueste
  más que una brújula (4 de hierro + redstone).
- Los tres ingredientes son de early game:
  - cobre: pico de piedra + horno;
  - vidrio: arena + horno;
  - redstone: pico de hierro. También sale de cofres, brujas y clérigos.
- El techo es el pico de hierro, más o menos la primera hora de juego.
- Además, `PortableWiringTest` (paquete portable, que no es mío) fija que la receta sea sin forma
  y de ≤ 3 ítems. Mantenerla evita tocar ese test.
- `SurvivalDataTest` comprueba con una tabla de progresión (0 = madera, 1 = piedra/horno,
  2 = pico de hierro) que ningún ingrediente pase del nivel 2.

**Desbloqueo** (`data/t3craft/advancement/recipes/tools/t3_terminal.json`):
- Antes se desbloqueaba solo al tener redstone.
- Ahora se desbloquea al recoger **cualquiera** de los tres ingredientes, igual que las recetas
  vanilla. En la práctica se ve en el libro desde el primer lingote de cobre.

**"Primer contacto"** (`data/t3craft/advancement/first_contact.json`):
- Pestaña *Minecraft* (padre `minecraft:story/root`), icono del terminal, con toast y anuncio en el
  chat.
- Su único criterio es `used_terminal` (`minecraft:impossible`). Solo lo concede
  `FirstContact`: un `UseItemCallback` del lado servidor que devuelve siempre `PASS`, así que el
  uso del ítem sigue igual.
- Se puede desactivar con `survival.firstContactAdvancement: false`.
- Abrir con la tecla **no** lo concede, porque la tecla no pasa por el servidor.
  `FirstContact.award(ServerPlayer)` es público por si portable quiere concederlo también desde
  la tecla en singleplayer.

## 2. Auditoría: qué necesita trucos, op o creativo

T3Craft es `environment: client`: corre en el cliente y en el servidor integrado. Todos sus
comandos son **comandos de cliente** (Fabric client commands), así que **ninguno exige op ni
trucos**. Eso es bueno para la UI y malo para lo que edita el mundo.

| Flujo | ¿Funciona en supervivencia sin trucos? | Notas |
|---|---|---|
| NPCs de hilos (`ThreadAvatar`, `RemotePlayer` solo de cliente) | Sí | No tocan el mundo ni la red. |
| HUD peek, toasts, notificaciones | Sí | |
| Ítem T3 Terminal (craftear y usar) | Sí, en singleplayer o LAN propia | En un servidor remoto el ítem no existe allí: no se craftea ni se recibe. |
| Tecla `Y` (terminal) | Sí, en cualquier mundo y sin el ítem | Es un atajo que no pide nada. La política permite exigir el ítem (opcional, por defecto no). |
| `/t3craft status/reconnect/reload/pair/unpair/envs`, `/t3craft config …`, tecla J | Sí | Solo cliente, sin efectos en el mundo. |
| `/t3dev …` (hot-swap) | Sí | Herramienta de desarrollo, sin efectos en el mundo. |
| `/t3craft debug build-demo` y `build-*` | **Sí, y es un truco** | Coloca una caseta de unos 225 bloques con un cofre **gratis**, sin op y en supervivencia, con `setBlock` directo en el `ServerLevel` integrado. **Propuesta:** exigir `debugWorldEdit` (trucos o creativo). |
| `Builders.service().build(...)` (lo usarán site y HQ) | Sí, gratis | Igual que arriba: bloques de la nada. **Propuesta:** `buildInWorld` + `materials` + `MaterialBill`. |
| Construcción en servidor remoto o dedicado | No | Necesita el servidor integrado (`IllegalStateException`). La política responde `needs_singleplayer`. |
| feat/site (nivelar, mover, undo; en curso) | Previsiblemente sí, gratis | Nivelar quita y pone bloques: debe seguir la misma política. Ver la pregunta abierta sobre los drops más abajo. |
| `/t3craft tp` (plan v2, no implementado) | No | Mandaría `/tp`, que exige trucos. Si se implementa: comprobar `PlayContext.cheats()` y avisar. |
| Modo aventura | UI: sí. Builders: deberían no | Los mapas de aventura protegen sus bloques. La política deniega sin trucos. |
| Espectador | UI: sí. Builders: no | La política siempre deniega. |
| Hardcore | Igual que supervivencia | |

## 3. Política (`dev.t3craft.survival`)

```
SurvivalRules (interfaz)          ← lo que consultan los demás
 └─ GameModePolicy(SurvivalSettings)   implementación por defecto, Java puro
Survival.rules()                  instancia viva (nunca null), Survival.reload() relee config
PlayContext(mode, cheats, integratedServer, terminalItem, hasTerminal)   datos puros
PlayContexts.of(Player, integrated)     Player → PlayContext (vale en cliente y servidor)
SurvivalClient.current(Minecraft)       atajo del lado cliente
MaterialBill                      lista de materiales: add(itemId), missing(inventario)
Verdict(allowed, reason)          reason → clave de idioma t3craft.survival.<reason>
```

| Método | Por defecto |
|---|---|
| `openTerminal(ctx)` | Siempre permitido (`terminalForKey: never`). Con `survival`/`always` exige llevar el terminal. **Nunca** lo exige si el ítem no puede existir (servidor remoto o `-Dt3craft.portable.item=false`), para no dejar a nadie sin acceso. |
| `buildInWorld(ctx)` | Requiere servidor integrado. Espectador: no. Aventura: solo con trucos. Supervivencia y creativo: sí. |
| `materials(ctx)` | `PLAYER_INVENTORY` en supervivencia y aventura, `FREE` en creativo (`builderMaterials: survival`). Los trucos no abaratan la supervivencia: para construir gratis, creativo o `never`. |
| `debugWorldEdit(ctx)` | Solo con trucos o en creativo (`debugBuilds: cheats`). |
| `firstContactAdvancement()` | `true` |

### Config (aditiva, en `config/t3craft.json`)

```json
"survival": {
  "terminalForKey": "never",
  "builderMaterials": "survival",
  "debugBuilds": "cheats",
  "firstContactAdvancement": true
}
```

Valores posibles:
- `terminalForKey` y `builderMaterials`: `never` | `survival` | `always`.
- `debugBuilds`: `cheats` | `any`.

Comportamiento:
- **Esta rama no escribe nunca el archivo.** Si la sección no está, se usan los valores por
  defecto, así que una instalación existente no cambia.
- `ConfigStore` (feat/config) conserva las secciones desconocidas al guardar, así que la sección
  sobrevive cuando se guarda desde la UI de ajustes.
- Un valor inválido usa el valor por defecto y deja un aviso en el log **solo con el nombre de la
  clave**.
- Se lee al iniciar el juego y con `/t3craft survival reload`.

## 4. Puntos de integración (para builders, site, portable y Taller)

Ninguno está aplicado: son paquetes de otras ramas.

**portable**: tecla del terminal, en `PortableClient`, dentro del bucle `consumeClick`:
```java
PlayContext ctx = SurvivalClient.current(mc);
Verdict v = ctx == null ? Verdict.OK : Survival.rules().openTerminal(ctx);
if (!v.allowed()) { mc.player.displayClientMessage(Component.translatable(v.langKey()), true); continue; }
```
Con los valores por defecto no cambia nada. Opcional, para conceder "Primer contacto" también
desde la tecla en singleplayer:
```java
UUID id = mc.player.getUUID();
mc.getSingleplayerServer().execute(() -> FirstContact.award(server.getPlayerList().getPlayer(id)));
```
(Comprobar null en ambos.)

**builders**: `/t3craft debug build-demo` (`BuildCommands.demo`), antes de crear la cuadrilla:
```java
Verdict v = Survival.rules().debugWorldEdit(SurvivalClient.current(Minecraft.getInstance()));
if (!v.allowed()) { src.sendError(prefix().append(Component.translatable(v.langKey()))); return 0; }
```

**builders / site / HQ**: ✅ **aplicado en feat/builders-survival** dentro de `BuildService.build` (ver
`docs/feat-builders.md`, «Supervivencia»): HQ y site solo tienen que llamar a `Builders.service().build(...)` y
capturar `IllegalStateException`. Boceto original: construcciones de verdad, del lado servidor y antes de `submit`:
```java
PlayContext ctx = PlayContexts.of(serverPlayer, PlayContexts.isIntegrated(serverPlayer));
SurvivalRules rules = Survival.rules();
Verdict v = rules.buildInWorld(ctx);                       // denegado → avisar y no construir
if (rules.materials(ctx) == MaterialSource.PLAYER_INVENTORY) {
    MaterialBill bill = new MaterialBill();
    for (Placement p : plan.placements()) {
        bill.add(BuiltInRegistries.ITEM.getKey(p.state().getBlock().asItem()).toString());
    }
    Map<String,Integer> missing = bill.missing(inventoryCounts(serverPlayer));
    // vacío → construir; si no → mostrar lo que falta
}
```

Propuesta de cobro: **comprobar todo al principio y descontar al colocar cada bloque**, con un
`BuildListener.placed` en el hilo servidor que quita un ítem. Si faltara a mitad de la obra
(porque el jugador tiró algo), pausar el job y avisar. Cancelar no cobra lo que falta.

Detalles para quien lo implemente:
- **Bloques dobles**: la mitad superior de puertas y la cabecera de camas mapean al mismo ítem.
  Hay que contar solo la mitad inferior/pie.
- **Bloques sin ítem**: `wall_torch` mapea a `torch` (correcto). Los bloques sin ítem dan
  `minecraft:air` y `MaterialBill` los ignora.
- **Contenido de block entities**: el NBT, como el contenido del cofre de la caseta, **no se
  cobra**. En supervivencia los planes no deberían traer botín, o hay que quitar el NBT del
  contenedor.
- **Nivelar o quitar bloques (feat/site)**: está abierto qué hacer con los bloques retirados.
  La propuesta es devolverlos al jugador (o a un cofre) en supervivencia y no devolver nada en
  creativo. Así nivelar no se convierte en una mina gratis ni en una forma de destruir sin
  esfuerzo. Necesita una decisión de diseño (Facundo).

**Taller**:
- Si se quiere que `/t3craft reload` también relea esta sección, añadir `Survival.reload()` en
  `T3CraftClient.reload()`.
- Para cambiar de política entera (por ejemplo, un mod de servidor con reglas propias):
  `Survival.install(miImplementación)`.

## 5. Cambios en archivos compartidos

| Archivo | Cambio |
|---|---|
| `T3Craft.java` | `SurvivalHooks.register()` tras `PortableItems.register()`. |
| `fabric.mod.json` | Nueva entrada `client`: `dev.t3craft.survival.client.SurvivalClient`. |
| `lang/en_us.json`, `lang/es_es.json` | Claves añadidas al final: `advancements.t3craft.first_contact.*` y `t3craft.survival.*`. |
| `advancement/recipes/tools/t3_terminal.json` | Criterios extra (cobre y panel de vidrio) en el mismo grupo OR. |

## 6. Verificación y pendientes

- `./gradlew build` en verde, con los 23 tests nuevos:
  - `GameModePolicyTest`
  - `SurvivalConfigTest`
  - `MaterialBillTest`
  - `SurvivalDataTest`: receta de early game, desbloqueo con cualquier ingrediente, criterio de
    "Primer contacto" igual al del código, traducciones de cada motivo.
- **No probado en el juego** (regla: no lanzar Minecraft). Las firmas de 26.3 (`UseItemCallback`,
  `PlayerAdvancements.award`, `Player.permissions()`, `Permissions.COMMANDS_GAMEMASTER`) se
  comprobaron con `javap` y el código compila. Pendiente de QA:
  - craftear en supervivencia;
  - ver la receta en el libro tras recoger cobre;
  - usar el terminal → toast de "Primer contacto";
  - `/t3craft survival` en supervivencia, creativo y aventura, con trucos y sin ellos.
- Pendiente: aplicar los puntos de integración de §4 en sus ramas.
- No se usó código de AgentCraft.
