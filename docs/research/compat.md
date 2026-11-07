# Compatibilidad: modo solo cliente, multiloader y versiones de Minecraft

Investigación (solo lectura) del 2026-11-07, hecha sobre `origin/main@f6364f7`, las ramas `origin/feat/site` y
`origin/feat/hq-design`, y la rama local `feat/survival`, que todavía no está en origin. `feat/workstation` y
`feat/demo-world` no tienen commits propios. Las fuentes web son de esa misma fecha. **SIN VERIFICAR** marca lo
que no se pudo confirmar en una fuente primaria ni en el juego.

## TL;DR

- **El modo "GUI only" se puede hacer, y el mod ya está casi ahí.** Los NPC son entidades de cliente y no tienen
  tipos registrados; la HUD, las pantallas, las teclas y los comandos son de cliente. Solo hay tres cosas que
  dependen del servidor:
  - el ítem `t3craft:t3_terminal`;
  - los datos de receta y advancement;
  - todo lo que usa `ServerLevel`: builders, site, HQ y survival/FirstContact.

  Ya hay detección de servidor integrado en parte del código. Falta centralizarla y apagar el ítem en servidores
  remotos.
- **Multiloader:** conviene un **núcleo común + plataformas Fabric/NeoForge al estilo MultiLoader-Template**
  (ModDevGradle + fabric-loom), sin Architectury API. Forge (MinecraftForge) solo vale la pena si se apunta a 1.20.1.
  Sinytra Connector no llega a 26.3.
- **Versiones:** 1.20.1 y 1.21.1 siguen siendo las más usadas en modpacks. Bajar de 26.x obliga a usar Java 21 o 17,
  remapping y APIs de render y GUI distintas. Conviene **quedarse en 26.x** hasta que el núcleo esté separado.
  Después, si hay demanda, abrir 1.21.1 NeoForge+Fabric con **Stonecutter**.

---

## 1. Modo "GUI only" (solo cliente)

### 1.1 Inventario de lo que depende del servidor o de registros

| Parte | Dónde | ¿Registro o servidor? | En un servidor remoto sin el mod |
|---|---|---|---|
| Ítem **T3 Terminal** | `main/.../portable/PortableItems.java:46` (`Registry.register(BuiltInRegistries.ITEM, …)` desde `ModInitializer`) | Registro de ítems (sincronizado) + pestaña creativa | Fabric deja entrar (§1.2), pero el ítem **no existe en el servidor**. Si el jugador lo saca de la pestaña creativa o lo mueve en un contenedor, el cliente manda un id que el servidor no conoce y lo más probable es que lo expulse (SIN VERIFICAR en el juego). |
| Receta + advancement de receta | `data/t3craft/recipe/t3_terminal.json`, `data/t3craft/advancement/recipes/tools/t3_terminal.json` | Datapack: solo lo carga un servidor que tenga el mod | No existen. No rompen nada. |
| **feat/survival**: advancement "Primer contacto", `FirstContact` (`UseItemCallback` del servidor), `SurvivalHooks.register()` en el entrypoint común | `survival/*` (rama local) | Datapack + evento de servidor | Nunca se otorga. `PlayContexts.isIntegrated` ya distingue los casos. Inofensivo. |
| **NPC** (`ThreadAvatar`, `BuilderAvatar` = `RemotePlayer`; paneles `Display.TextDisplay`) | `client/npc/NpcRegistry.java:380-396`, `behavior/BehaviorDirector.java:289-297`, `build/client/BuildCrew.java:73` | **Ninguno.** Se agregan con `ClientLevel.addEntity`, sin `EntityType` propio ni red | **Funcionan.** Quedan dos riesgos (abajo). |
| Clic sobre un NPC | `client/T3CraftClient.java:104` (`UseEntityCallback` devuelve `SUCCESS`) | Ninguno | Con `SUCCESS`, Fabric probablemente siga mandando `ServerboundInteractPacket` con el id de la entidad falsa (SIN VERIFICAR). El servidor lo ignora si no conoce el id. Si el id **choca** con una entidad real, interactúa con ella. Conviene cortar el paquete. |
| **Construcción** (`BuildManager`, `ServerLevelSink`) | `main/.../build/*`, `build/client/BuildersClient.java:81-107` | `ServerLevel` del servidor integrado | Ya se bloquea: "builders need a singleplayer world (integrated server)". |
| **feat/site** (`SiteRuntime`, `LevelBlockWorld`, `LevelTerrain`) | `site/mc/*` | `ServerLevel` del servidor integrado | Igual que builders: no funciona en remoto. La previsualización con partículas sí podría funcionar en cliente. |
| **feat/hq-design** (`PlanApplier`, `/t3craft debug hq-preview`) | `hq/*` | `ServerLevel` | No funciona en remoto. `HqGenerator`/`HqLayout` son lógica pura y sí son reutilizables. |
| workstation / demo-world | sin código todavía | Si colocan bloques, necesitan `ServerLevel` | Mismo criterio que builders. |
| HUD (`PeekHud`), pantallas, teclas, comandos de cliente, toasts, config, link/T3 | `client/*`, `t3/*` | Ninguno | Funcionan sin cambios. |

**Riesgos de las entidades de cliente en un servidor remoto:**
1. **Ids de entidad.** El cliente las crea con ids de su propio contador. El servidor asigna los suyos por separado, así
   que puede haber colisiones (SIN VERIFICAR, poco probable pero posible). Dos mitigaciones: usar ids negativos o muy
   altos al crearlas, y no mandar nunca paquetes de interacción para ellas.
2. **Anticheats.** Plugins como Grim o Vulcan pueden marcar como sospechoso un paquete de interacción con un id que no
   existe (SIN VERIFICAR). La mitigación es la misma: no mandar el paquete.

### 1.2 Qué pasa al entrar a un servidor sin el mod

| Cargador | Servidor vanilla | Servidor del mismo cargador sin el mod | Fuente |
|---|---|---|---|
| **Fabric** + Fabric API | Entra. No llega sync y Fabric pone los ids vanilla primero ("crucial for vanilla server compatibility"). | Entra. Solo falla si el **servidor** manda entradas que el cliente no tiene. Las entradas que solo están en el cliente reciben un id nuevo ("assuming client only registry entry"). | [RegistrySyncManager](https://github.com/FabricMC/fabric/blob/26.3/fabric-registry-sync-v0/src/main/java/net/fabricmc/fabric/impl/registry/sync/RegistrySyncManager.java), [MappedRegistryMixin](https://github.com/FabricMC/fabric/blob/26.3/fabric-registry-sync-v0/src/main/java/net/fabricmc/fabric/mixin/registry/sync/MappedRegistryMixin.java), [ClientRegistrySyncHandler](https://github.com/FabricMC/fabric/blob/26.3/fabric-registry-sync-v0/src/client/java/net/fabricmc/fabric/impl/client/registry/sync/ClientRegistrySyncHandler.java) |
| **NeoForge** 26.3 | Entra, salvo que el mod tenga payloads **no opcionales**, enums extendidos o feature flags. Las entradas de registro no se comprueban. | Entra. `applySnapshot` solo falla con entradas del **servidor** que faltan en el cliente. Las del cliente quedan sin id mapeado; si eso crashea o solo deja el ítem inútil está SIN VERIFICAR. | [ClientNetworkRegistry](https://github.com/neoforged/NeoForge/blob/26.3.x/src/client/java/net/neoforged/neoforge/client/network/registration/ClientNetworkRegistry.java), [RegistryManager](https://github.com/neoforged/NeoForge/blob/26.3.x/src/main/java/net/neoforged/neoforge/registries/RegistryManager.java), [PayloadRegistrar.optional()](https://github.com/neoforged/NeoForge/blob/26.3.x/src/main/java/net/neoforged/neoforge/network/registration/PayloadRegistrar.java) |
| **Forge** | Los mods de cliente sin canales "can't cause a mismatch". Con ítems: SIN VERIFICAR. | Ídem | [Forge handshake (wiki)](https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Minecraft_Forge_Handshake) |

**Marcar el mod como "solo cliente" en cada cargador:**

| Cargador | Mecanismo | Notas |
|---|---|---|
| Fabric | `"environment": "client"` en `fabric.mod.json` (**ya está**) | Solo decide dónde carga el mod; el servidor integrado sí lo carga. No hay equivalente de `displayTest` ni handshake de lista de mods ([spec](https://wiki.fabricmc.net/documentation:fabric_mod_json_spec)). No hay API para excluir una entrada del sync ([fabric-api#2168](https://github.com/FabricMC/fabric-api/issues/2168), abierto). |
| NeoForge | `@Mod(value = "t3craft", dist = Dist.CLIENT)` y `side = "CLIENT"` en las dependencias de `neoforge.mods.toml` | `displayTest` **se eliminó** en FML en marzo de 2024 ([FML#103](https://github.com/neoforged/FancyModLoader/pull/103)). No hay `clientSideOnly` ([docs](https://docs.neoforged.net/docs/concepts/sides)). Si alguna vez hay payloads, registrarlos con `.optional()`. |
| Forge | `clientSideOnly = true` en `mods.toml` (desde 1.20.x, con backport a 1.20.1) | Fija `displayTest = IGNORE_ALL_VERSION` (sin X roja en la lista de servidores) y no carga el mod en servidores dedicados ([PR #9804](https://github.com/MinecraftForge/MinecraftForge/pull/9804), [docs](https://docs.minecraftforge.net/en/latest/gettingstarted/modfiles/)). |

### 1.3 Cómo hacer que se desactive solo

**Detección** (cliente, Mojmap 26.x; [Minecraft.java decompilado 26.2](https://github.com/xiegeezr886/WurstB-Plus/blob/95086e814f25138ac6f1be638c089f2d156cdd90/_recon/nf262-fullsrc/net/minecraft/client/Minecraft.java), fuente de terceros):

| Situación | `hasSingleplayerServer()` | Funciones del servidor (build/site/HQ) |
|---|---|---|
| Singleplayer | `true` | Sí |
| Mundo abierto a LAN, anfitrión | `true` (`getSingleplayerServer().isPublished()`) | Sí |
| Invitado LAN, servidor dedicado, Realms (`getCurrentServer().isRealm()`) | `false` | No: solo GUI y NPC |

**Propuesta, sin implementar:**
1. Una sola fuente de verdad, `WorldCapabilities` en el núcleo: `{integratedServer, terminalItemKnownByServer}`. Se
   calcula al unirse a un mundo (`ClientPlayConnectionEvents.JOIN` / `ClientPlayerNetworkEvent.LoggingIn`).
   Builders, site, HQ, workstation y survival la consultan en vez de comprobar el servidor cada uno por su cuenta.
2. **Ítem del terminal.** Hay tres opciones, de la más segura a la menos:
   - **(a) Recomendada a mediano plazo.** No registrar un ítem propio. El "terminal" sería un ítem vanilla (por
     ejemplo `minecraft:recovery_compass`) con el componente `minecraft:item_model` apuntando a
     `t3craft:t3_terminal` y un `custom_data` `{t3craft:terminal}`. La receta del datapack produce ese stack. En
     remoto es un ítem vanilla normal, así que no se necesita sync y no hay expulsión. Se puede porque
     `item_model` existe desde 1.21.2 ([blog Fabric 1.21.2](https://github.com/FabricMC/fabricmc.net/blob/main/_posts/2024-10-14-1212.md)).
     Contras: sin el mod, el ítem se ve como el vanilla base, y en versiones anteriores a 1.21.2 haría falta otro
     camino.
   - **(b) Corto plazo.** Mantener el ítem, pero **sacarlo de la pestaña creativa** cuando
     `!hasSingleplayerServer()`. Además, interceptar en el cliente el uso o clic del ítem en remoto para que no salgan
     paquetes con él. Con eso se evita la expulsión más probable.
   - **(c)** Un flag de arranque `t3craft.portable.item=false`, que ya existe como propiedad de sistema y podría
     pasar a la config, para usar en modpacks orientados a multijugador. Los registros se congelan al arrancar, así
     que no se puede decidir por servidor.
3. **NPC.** Que `UseEntityCallback` no deje salir el paquete para las entidades falsas: devolver `FAIL` o cancelar
   del lado cliente (confirmar en la API cuál de los dos corta el envío). Crearlas con ids fuera del rango del
   servidor.
4. Un **toggle de "overlay mode"** en la config para servidores donde no se quiere ver NPC en el mundo (por reglas
   del servidor o por rendimiento). En ese modo se usan solo PeekHud y PortableScreen como "pantalla de agentes".
5. Mensajes: en remoto, `/t3craft build|site|hq` responde "solo en singleplayer o como anfitrión LAN". Builders ya
   lo hace.

---

## 2. Opciones multiloader

Datos del 2026-11-07, sacados de los metadatos de maven y de las APIs de GitHub y Modrinth.

| Opción | Estado / último release | Soporta 26.3 | Pros | Contras | Costo de mantenimiento |
|---|---|---|---|---|---|
| **Architectury API + Architectury Loom** | API **22.0.5** (2026-10-09, Fabric+NeoForge) ([Modrinth](https://api.modrinth.com/v2/project/architectury-api/version)). Loom **1.17.493** (2026-09-12) ([maven](https://maven.architectury.dev/dev/architectury/architectury-loom/maven-metadata.xml)). Plugin **3.5.170**. | Sí. Loom también maneja **Forge sin ofuscar** ([PR #352](https://github.com/architectury/architectury-loom/pull/352)) | Abstracciones de eventos, registros y red ya hechas. Muy usado. | Agrega una **dependencia de runtime** que el jugador tiene que instalar. La API **no publica Forge desde 1.20.4** (11.1.17, 2024-03). Loom propio, un fork que va por detrás del upstream. | Medio. Hay que esperar sus ports en cada versión. |
| **MultiLoader-Template (jaredlll08)** | Rama por versión hasta **26.3** (por defecto). "Port to 26.3" el 2026-09-16, último push el 2026-10-01 ([repo](https://github.com/jaredlll08/MultiLoader-Template)) | Sí: ModDevGradle 2.0.147 + fabric-loom non-remap, Java 25 | **Sin dependencias de runtime**. Herramientas oficiales de cada cargador. `common` compila contra vanilla (NeoForm). Patrón conocido (servicios con `ServiceLoader`). | No trae abstracciones: los eventos y registros se escriben a mano por plataforma. Sin Forge. Versiones fijadas viejas (Loom 1.18.1, Loader 0.19.5) que habría que subir. | **Bajo-medio.** Con lo poco que usamos de Fabric API (abajo), encaja bien. |
| **Sinytra Connector + Forgified Fabric API** (Fabric sobre NeoForge) | Connector **3.1.0-beta.4+26.2** (2026-10-30, beta). FFAPI **0.161.0+26.2** (2026-10-23) ([Connector](https://api.modrinth.com/v2/project/connector/version), [FFAPI](https://api.modrinth.com/v2/project/forgified-fabric-api/version)) | **No** (llega a 26.2) | Cero trabajo nuestro. | Beta, siempre una versión atrás, y el usuario tiene que instalar dos mods extra. Lo decide el jugador, no nosotros. No sirve como estrategia de distribución. | Ninguno, pero no lo controlamos. |
| **Núcleo común + plataformas propias** (sin plantilla) | — | — | Control total. | Hay que mantener el build a mano. La plantilla de jaredlll08 ya resuelve justo esto. | Medio |
| **Forge (MinecraftForge)** como plataforma extra | Forge 26.3: **66.1.5** (rec. 66.1.0); 1.20.1: 47.4.28 ([promotions](https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json)) | Sí | Fuerte en 1.20.1. | En 1.21+/26.x los packs grandes son NeoForge (ATM10, ATM11). Las herramientas multiloader lo dejaron. | Alto para lo que aporta, salvo en 1.20.1. |

**Cuánto Fabric usamos hoy:** sin mixins. Fabric aparece en 13 archivos:
- eventos de tick y de ciclo de vida, del cliente y del servidor;
- comandos de cliente, `KeyMappingHelper` y `HudElementRegistry`;
- `CreativeModeTabEvents` y `UseEntityCallback`;
- `FabricLoader` (config dir, isDevelopment), ModMenu (opcional).

Todo tiene equivalente en NeoForge:

| Fabric | NeoForge 26.3 ([eventos cliente](https://github.com/neoforged/NeoForge/tree/26.3.x/src/client/java/net/neoforged/neoforge/client/event)) |
|---|---|
| `ClientTickEvents`, `ClientLifecycleEvents` | `ClientTickEvent.Pre/Post`, `ClientStartedEvent`/`ClientStoppingEvent` |
| `ServerLifecycleEvents`, `ServerTickEvents` | `ServerStartedEvent`/`ServerStoppingEvent`, `ServerTickEvent.Post` |
| `ClientCommandRegistrationCallback` | `RegisterClientCommandsEvent` |
| `KeyMappingHelper.registerKeyMapping` | `RegisterKeyMappingsEvent#register` / `#registerCategory` |
| `HudElementRegistry.addLast` | `RegisterGuiLayersEvent#registerAboveAll` |
| `CreativeModeTabEvents.modifyOutputEvent` | `BuildCreativeModeTabContentsEvent` |
| `UseEntityCallback` | `PlayerInteractEvent.EntityInteract` |
| `FabricLoader.getConfigDir()` | `FMLPaths.CONFIGDIR` |
| ModMenu | `IConfigScreenFactory` (extension point) |

---

## 3. Versiones de Minecraft

### 3.1 Popularidad

Datos de Modrinth: `total_hits` en `api.modrinth.com/v2/search` con faceta `versions:` del 2026-11-07. Las
estadísticas por versión de CurseForge no se encontraron (SIN VERIFICAR).

| Versión | Mods | Modpacks | Packs insignia |
|---|---|---|---|
| **1.20.1** | 35 132 | 7 421 | Muchos Forge |
| **1.21.1** | 32 855 | 5 252 | ATM10 (NeoForge) |
| 1.21.11 | 20 098 | 2 989 | |
| 26.1.2 | 15 757 | 1 410 | ATM11 0.14.0 beta (NeoForge, 2026-11-03, [CurseForge](https://www.curseforge.com/minecraft/modpacks/all-the-mods-11), dato de un resumen de búsqueda) |
| 26.2 | 14 636 | — | |
| **26.3** (actual) | 9 959 | 850 | |

### 3.2 Qué cambia en cada salto (hacia atrás desde 26.3)

| Cambio | Versión | Impacto en t3craft | Fuente |
|---|---|---|---|
| Jars sin ofuscar, **Java 25**, Loom non-remap, Fabric API con nombres Mojang. Yarn deja de recibir soporte. | 26.1 | Estamos aquí. Antes de 26.1 hace falta Loom con remap y Java 21. | [Fabric 26.1](https://fabricmc.net/2026/03/14/261.html), [Mojang](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition) |
| `GuiGraphics` pasa a llamarse **`GuiGraphicsExtractor`** y `render` pasa a ser `extractRenderState` | 26.1 | Todas las pantallas (7 archivos) | [primer 26.1](https://github.com/neoforged/.github/blob/main/primers/26.1/index.md) |
| `HudRenderCallback` se elimina | 26.1 | — (usamos `HudElementRegistry`) | [Fabric 26.1](https://fabricmc.net/2026/03/14/261.html) |
| Estado de pantalla y HUD se mueve de `Minecraft` a `Gui`/`Hud` (`mc.gui.setScreen`); se elimina `isSingleplayer` | 26.2 | `setScreen`, detección | [Fabric 26.2](https://github.com/FabricMC/fabricmc.net/blob/main/_posts/2026-06-15-262.md), [primer 26.2](https://github.com/neoforged/.github/blob/main/primers/26.2/index.md) |
| `ResourceLocation` pasa a llamarse **`Identifier`** (Mojmap) | 1.21.11 | Por todas partes | [primer 1.21.11](https://github.com/neoforged/.github/blob/main/primers/1.21.11/index.md) |
| `KeyMapping.Category` record + `Category.register` | 1.21.9 | `T3CraftKeys`, `PortableClient` (ya tienen fallback) | [primer 1.21.9](https://github.com/neoforged/.github/blob/main/primers/1.21.9/index.md) |
| Render de GUI en dos etapas (`GuiRenderState`); `HudElementRegistry` de Fabric | 1.21.6 | HUD y pantallas | [primer 1.21.6](https://github.com/neoforged/.github/blob/main/primers/1.21.6/index.md), [Fabric 1.21.6](https://github.com/FabricMC/fabricmc.net/blob/main/_posts/2025-06-15-1216.md) |
| `Item.Properties#setId` obligatorio; componente `item_model` | 1.21.2 | Ítem del terminal y la opción (a) | [primer 1.21.2](https://github.com/neoforged/.github/blob/main/primers/1.21.2/index.md) |
| **Componentes de ítem** en vez de NBT; **Java 21** | 1.20.5 | `custom_data`, block entities en build (`loadWithComponents`) | [primer 1.20.5](https://github.com/neoforged/.github/blob/main/primers/1.20.5/index.md) |
| `GuiGraphics` (antes `PoseStack`/`GuiComponent`) | 1.20 | — | [primer 1.20](https://github.com/neoforged/.github/blob/main/primers/1.20/index.md) |
| Entidades **Display** (TextDisplay) | 1.19.4 | Paneles y burbujas de NPC: presentes en todas las versiones candidatas | [wiki](https://minecraft.wiki/w/Text_Display) |

**Java:** 1.18–1.20.4 usa Java 17, 1.20.5–1.21.11 usa Java 21 y 26.1+ usa Java 25
([wiki](https://minecraft.wiki/w/Tutorial:Update_Java)). Hoy el núcleo compila con `release 25`. Para compartirlo con
1.21.1 tendría que compilar con `release 21`, y con 1.20.1, con `release 17`: nada de API de Java 22+ en el núcleo.

**Mappings:**
- Yarn/Intermediary llegan solo hasta 1.21.11 ([Fabric](https://fabricmc.net/2025/12/05/12111.html)).
- Parchment publica hasta **1.21.11** y no tiene datos para 26.x ([maven](https://ldtteam.jfrog.io/artifactory/parchmentmc-public/org/parchmentmc/data/)).
- Para versiones anteriores a 26.1 conviene **Mojmap (+ Parchment)**, así los nombres coinciden con los de 26.x y el
  código compartido diverge menos.

**Costo estimado por versión hacia atrás:**
- **26.1/26.2:** casi trivial (pocas diferencias de API).
- **1.21.11:** medio. Cambian la toolchain de remap y Java 21, y faltan los renombres de 26.1 (`GuiGraphicsExtractor`).
- **1.21.1:** alto. Además, GUI antes de 1.21.6, `KeyMapping` sin categorías record, `ResourceLocation` y no existe
  `item_model`.
- **1.20.1:** muy alto. Además, Java 17, NBT en vez de componentes, Forge en vez de NeoForge y `setId` inexistente.

### 3.3 Estrategias multiversión

| Estrategia | Estado | Pros | Contras |
|---|---|---|---|
| **Stonecutter** (kikugie) | **0.9.8** estable (2026-08-31, [Gradle Plugin Portal](https://plugins.gradle.org/plugin/dev.kikugie.stonecutter)). El desarrollo pasó a [Codeberg](https://codeberg.org/stonecutter/stonecutter), con 0.10-beta.4 el 2026-11-04. | Un solo árbol, comentarios `//? if >=1.21.6` con semver y reemplazos/"swaps". Funciona con cualquier cargador y combina bien con MultiLoader. Tiene plugin de IntelliJ. | El código se ensucia con condicionales. Hay que aprender su DSL, y combinar remap con non-remap Loom entre versiones no está documentado (SIN VERIFICAR). |
| **Preprocessor (ReplayMod)** / essential-gradle-toolkit | Preprocessor activo (commit 2026-11-03, [repo](https://github.com/ReplayMod/preprocessor)). EGT **0.7.3** (2026-10-06), con defaults de Forge y NeoForge hasta 26.3. | **Remapea** automáticamente entre mappings y versiones, y está muy probado (ReplayMod, Essential). | Más complejo y opinado. El build es frágil y pesado. |
| **Ramas por versión** | — | Simple: cada rama es un mod normal. | Cada fix se cherry-pickea N veces y las ramas divergen. Solo sirve con 1–2 versiones y pocos cambios. |
| **Núcleo puro en JAR aparte** (sin MC) + adaptadores finos por versión | — | Esa parte del código no necesita preprocesador. Ya tenemos ~40 % del código sin imports de MC. | Las vistas (pantallas, NPC) igual se duplican o se condicionan. |

Proporción actual sin MC (archivos no-test):
- **sin imports de MC:** `t3/*` (1785 líneas), `client/link/*`, `debug/*`, gran parte de `client/npc/behavior`,
  `client/config` y `client/portable` (estado y navegación), y `hq/HqGenerator` y `site/SiteService` (lógica pura);
- **con imports de MC o Fabric:** pantallas, registros de NPC, `ServerLevelSink`/`site/mc` y entrypoints.

---

## 4. Recomendación

**Objetivo:** un jar por cargador que funcione en cualquier servidor (modo GUI) y se active completo en
singleplayer o como anfitrión LAN. Primero 26.x en Fabric + NeoForge; versiones anteriores solo si hay demanda.

| # | Paso | Por qué ahora | Esfuerzo |
|---|---|---|---|
| 1 | **Endurecer el modo remoto en Fabric.** Agregar `WorldCapabilities` centralizado. Quitar el terminal de la pestaña creativa en remoto y no mandar paquetes de uso con él. Que `UseEntityCallback` no deje salir el paquete para los NPC. Ids de entidades de cliente fuera de rango. Mensaje uniforme para build/site/HQ. Un toggle "overlay only". | Arregla riesgos reales en multijugador ya, sin tocar el build. | 1–2 días |
| 2 | **Separar núcleo y plataforma dentro del mod actual**, sin cambiar todavía el build: un paquete `dev.t3craft.platform` con interfaces (`Platform.events()`, `keys()`, `hud()`, `commands()`, `paths()`, `creativeTab()`) y una implementación Fabric. Ninguna clase fuera de `platform/fabric` importa `net.fabricmc.*`. Lo mismo para `ServerLevel`: todo por `BlockSink`/`BlockWorld` (site ya lo hace con `site/mc`, y HQ con `PlanApplier`). Pedirlo como regla a las ramas en curso (site, HQ, workstation, survival). | Es el refactor que mantiene abiertas las demás opciones. Son ~13 archivos con imports de Fabric y no hay mixins. | 2–3 días |
| 3 | **Ítem del terminal como ítem vanilla + `item_model`** (opción 1.3-a), con receta que produce el stack con componente. Survival/FirstContact lo detecta por `custom_data`. | Se elimina el único registro sincronizado: el mod pasa a ser **puramente de cliente** en remoto y queda listo para NeoForge sin sync. | 1 día |
| 4 | **Pasar a MultiLoader-Template (26.3):** `common` (NeoForm/vanilla), `fabric` y `neoforge` (ModDevGradle). Implementación `platform/neoforge` con la tabla de §2. `neoforge.mods.toml` con `dist = CLIENT` en `@Mod`. | Llega a ATM11 y a los packs NeoForge 26.x sin depender de Architectury ni de Connector. | 4–6 días (build, CI, GameTests en ambas plataformas) |
| 5 | (Opcional) **Agregar 26.1.2/26.2 con Stonecutter** encima del paso 4. | 26.1.2 tiene 1,6× más packs que 26.3, y las diferencias de API son pocas. | 2–4 días |
| 6 | (Solo con demanda) **1.21.1 NeoForge+Fabric**: núcleo con `release 21`, Mojmap+Parchment, pantallas condicionadas antes y después de 1.21.6, sin `item_model` (otro truco de modelo). **1.20.1/Forge: no recomendado**: Java 17, NBT, Forge con `clientSideOnly` y GUI vieja. Es casi otro mod. | 1.21.1 tiene ~5 k packs. | 1.21.1: 2–3 semanas; 1.20.1: 3–5 semanas extra |

**Descartado por ahora:**
- **Architectury API:** agrega una dependencia de runtime para el jugador y nos resuelve poco, porque usamos poca
  API.
- **Sinytra Connector:** no llega a 26.3, es beta y no lo controlamos. Se puede documentar como "puede funcionar"
  sin garantías.
- **Forge 26.x:** su ecosistema es marginal frente a NeoForge.

**Reglas para las ramas en curso, para no cerrarse puertas:**
- Nada de `net.fabricmc.*` fuera de los entrypoints o de `platform`.
- Nada de `ServerLevel` fuera de adaptadores (`*/mc/*`, `ServerLevelSink`).
- No registrar ítems, bloques ni entidades nuevos: si hacen falta, que sean vanilla + componentes.
- Sin payloads de red; si alguna vez hacen falta, opcionales.
- La lógica de dominio (`hq/HqGenerator`, `site/SiteService`, `npc/behavior`, `t3/*`) libre de MC, y sin API de
  Java 22+ si se quiere bajar a 1.21.x.
