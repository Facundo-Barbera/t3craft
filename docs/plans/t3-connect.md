# T3 Connect para T3Craft: investigación (issue #1)

Estado: **solo investigación, no se implementa nada**. Lectura de solo lectura hecha el 2026-11-09.

**Fuentes**
- Este repo en `origin/main`.
- `pingdotgg/t3code` en el commit `33db45effd176c01a27b3f2976192e77ad82b301` (2026-11-09, `apps/server` v0.1.7, alfa). Los contratos vendorizados en `bridge/vendor/t3-contracts` son del commit `25bf84ab` (2026-11-07).

Lo que no se pudo confirmar se marca **«No confirmado»**. No se probó nada contra un T3 real.

## 1. Estado actual (camino C) y sus puntos débiles

En modo `direct` (`ModConfig.Mode.DIRECT`, el predeterminado), el mod se empareja **con cada entorno por separado**:

1. En el host: `t3 auth pairing create --scope orchestration:read --scope orchestration:operate --ttl 5m`.
2. `Pairing`/`PairingClient` canjean el enlace en `POST /oauth/token` (token-exchange con `environment-bootstrap`) y piden exactamente esos scopes. `Pairing.checkGranted` rechaza cualquier scope de más.
3. `Environments` guarda `environments.json` (`{id,label,url}`) y un Bearer por entorno en `credentials/<id>.json` (más `credentials/operate/<id>.json`, que es el interruptor del control).
4. `DirectController` crea una `T3Connection` por entorno: ticket en `/api/auth/websocket-ticket/rpc` y luego `/ws?orchestrationProtocol=2&wsTicket=…`. `OperateConnection` abre otro socket bajo demanda.

Puntos débiles:
- Hay que copiar a mano una URL y un enlace por cada entorno.
- La `url` debe ser alcanzable desde la máquina que corre Minecraft (Tailscale, LAN o un `https` público).
- La sesión dura 30 días y no tiene refresh. Al caducar hay que emparejar de nuevo.
- Es lo único que ya funciona, está probado y tiene scopes mínimos verificados.

## 2. Cómo funciona T3 Connect (según el código)

Fuentes: `docs/internals/t3-connect.md`, `docs/internals/environment-auth.md`, `docs/operations/connect-setup.md`, `docs/user/remote-access.md`, `packages/contracts/src/relay.ts`, `infra/relay/src/{auth,http}`, `packages/client-runtime/src/{relay,authorization}`, `apps/server/src/cloud/CloudLink.ts` y `apps/server/src/cli/{connect,up}.ts`.

**Lado host (el entorno).**
- `t3 connect` (o `t3 up --connect`) hace login del CLI en Clerk y enlaza **esa máquina** con la cuenta.
- El relay (Worker de Cloudflare, `relay.t3.codes`) le asigna un túnel gestionado. En el host corre `cloudflared`, que sale por el puerto 7844 (TCP y UDP).
- Una cuenta puede enlazar hasta **3 máquinas**. Un plan de pago de T3 Chat sube el límite a 10 o 100.

**Lado cliente (apps oficiales).**
1. Login con Clerk. Se obtiene un JWT con la plantilla `t3-relay` y audiencia `t3-code-relay`.
2. `POST /v1/client/dpop-token`: canje de ese JWT por un token de acceso del relay **atado a una clave DPoP** (ES256). Solo acepta `client_id ∈ {t3-mobile, t3-web}` y los scopes `environment:connect` y `environment:status`.
3. `GET /v1/environments` lista los entornos enlazados, con `{environmentId,label,endpoint{httpBaseUrl,wsBaseUrl}}`.
4. `POST /v1/environments/:id/connect` (DPoP, con la huella de la clave del cliente). El relay pide al entorno, **a través del túnel**, `POST /api/t3-connect/mint-credential` con una prueba JWT firmada por el relay.
5. El entorno crea un enlace de emparejamiento (`subject: cloud-connect`, TTL 2 min) **atado a la clave DPoP del cliente**. Devuelve `{endpoint, credential}`.
6. El cliente canjea esa credencial **directo con el entorno** en `/oauth/token` (con prueba DPoP). Desde ahí el tráfico va al hostname del túnel y **el relay no está en el camino**.

Detalles relevantes:
- El WebSocket lleva una prueba DPoP en el query param `dpop`, y el ticket queda atado a la clave.
- Los tokens de T3 Connect duran **1 hora**. El driver los renueva en el último minuto con el socket vivo, y si falla el socket se cierra y el supervisor reconecta (`connection-runtime.md`).

**Respuestas a las preguntas del issue.**
- **¿Cliente de terceros?** No hay nada público que lo prevea.
  - El `client_id` es una unión cerrada (`t3-mobile`/`t3-web`) en `RelayTokens.ts`.
  - El canje exige un JWT de sesión de Clerk con la plantilla de T3. El Clerk de producción y la app OAuth del CLI son de T3 Tools Inc.
  - No encontré documentación de API para terceros, registro de clientes ni términos de uso. **No confirmado** que usar el `client_id` de `t3-web` desde un mod esté permitido. Sería hacerse pasar por la app oficial.
- **¿Token por entorno con solo `read`?** Sí se puede, pero **lo decide el cliente, no el relay**.
  - `mint-credential` emite siempre `AuthStandardClientScopes`: 16 scopes, entre ellos `terminal:operate`, `filesystem:write`, `access:write` y `relay:write`.
  - El canje en `/oauth/token` solo puede **estrechar** (intersección con el grant, `EnvironmentAuth.ts`). Pedir `scope=orchestration:read` funciona, pero es una cortesía del cliente.
  - La credencial de cuenta equivale, en la práctica, a **acceso total a todas las máquinas enlazadas**. El relay no tiene scopes por entorno.
- **¿Login desde Minecraft?** El CLI de T3 usa OAuth con PKCE (loopback `127.0.0.1:34338`) o el device grant de Clerk para sesiones sin navegador. Pero ese token de la app OAuth del CLI solo pasa por los endpoints con Bearer (listar y enlazar, `verifyClerkOAuthBearerToken`). **El canje DPoP exige el JWT de sesión**, no el token del CLI (`Api.ts`, `verifyClerkBearerToken`). Un mod no puede obtener ese JWT sin registrar su propia app en el Clerk de T3 o reutilizar el flujo de las apps oficiales.
- **¿Renovación?** Hay un token de relay y tokens de entorno de 1 h con renovación en caliente, más el refresh de Clerk (`offline_access`). Eso implica que el mod guardaría un refresh token de **cuenta**.

## 3. Comparación de los tres caminos

### A) Cliente nativo en Java

- **Qué hace falta.**
  - Clerk: login (un JWT que hoy solo emiten las apps oficiales) y refresh.
  - Relay: `dpop-token`, `/v1/environments`, `connect` y `status`.
  - DPoP ES256: el JDK 25 lo cubre con `java.security` y `HttpClient`, sin dependencias nuevas.
  - Canje en el entorno con `scope=orchestration:read[+operate]`.
  - Adaptar `T3Connection` y `OperateConnection`: Bearer → DPoP, `dpop` en el upgrade, renovación cada hora.
- **Auth y tokens.**
  - El mod guardaría el refresh de Clerk (cuenta) y la clave privada DPoP.
  - Los tokens por entorno serían efímeros (1 h), y podrían ni escribirse a disco.
  - No hay keystore del sistema accesible desde el mod, así que irían a un archivo con permisos 600.
- **En la máquina del jugador.** Solo Minecraft. No necesita Node ni `cloudflared`.
- **Latencia y robustez.**
  - Camino extra: Cloudflare, el túnel y el host.
  - Si el host está dormido, el túnel cae. Hay un máximo de 3 máquinas por cuenta.
  - El mod tendría que replicar el supervisor de reconexión (el de upstream reintenta con backoff hasta 5 min, vigila sockets muertos, etc.).
- **Mantenimiento.**
  - Se suma un segundo protocolo: relay + Clerk + DPoP + mint. `relay.ts` tiene 1329 líneas.
  - Mantener a mano el contrato de relay en Java es lo que la poda del bridge ya mostró como costoso (27.6k líneas vendorizadas frente a 1.7k propias).
  - Upstream congela contratos para sus apps instaladas (ej. `listDevices` v1 frente a v2), pero **no promete nada a terceros**.
- **Seguridad.** Peor que C: un refresh de cuenta robado da `terminal:operate` en todas las máquinas. Mitigación parcial: DPoP ata los tokens a la clave. Pero quien robe el archivo del mod se lleva también la clave.
- **Esfuerzo en el mod.**
  - Paquete nuevo `dev.t3craft.t3.connect` (Clerk, relay, DPoP, mint): unos 8–12 archivos.
  - Cambios en `t3` (`T3Connection`, `OperateConnection`, `Environments`) y en `client.screen`/`client.config` (login, lista de entornos).
  - Tamaño: **L**, varias semanas con revisión. Es una estimación mía, sin medir.
- **Riesgos.**
  - **Bloqueante**: sin permiso de upstream para un cliente de terceros, depende de suplantar a `t3-web`.
  - Cualquier cambio de Clerk o del relay rompe el mod sin aviso.
  - La beta de device-grant de Clerk (el CLI lo marca como beta) no sirve a terceros.

### B) CLI de T3 Code como sidecar solo-backend

- **Hallazgos sobre el CLI** (`apps/server/src/binCli.ts`):
  - `t3`/`t3 serve`/`t3 start` levantan un servidor **completo**: SQLite, proveedores y agentes. No encontré un modo «cliente headless» ni un flag que desactive la ejecución.
  - `t3 connect` es del **lado host**: enlaza *esa* máquina para ser alcanzable. No lista ni conecta a otros entornos.
  - `t3 environment link <url> --pairing-code` crea enlaces servidor→servidor por MCP (agentes que trabajan en otro entorno). No es el protocolo de shell/streams que usa el mod.
  - `t3 thread list|start|send|show` habla con **el servidor local en marcha**.
  - `acp-mcp-bridge` (stdio) es interno y no listado. Solo expone herramientas MCP de hilos, sin `subscribeShell`.
  - El cliente de relay (`packages/client-runtime/relay/*`) vive en un paquete **privado** (`"private": true`). No se publica en npm ni lo expone el CLI.
  - Conclusión: **hoy no existe lo que describe el issue**. El camino B tal como se planteó necesita una función nueva en upstream: un modo cliente que haga login de cuenta, descubra entornos y exponga localmente el shell y los streams.
- **Qué hace falta.**
  - Distribuir o exigir el binario `t3` (instalador `curl t3.codes/install.sh`, o Node ≥ 22.16 con `npx t3`). Con un servidor completo local, el jugador estaría corriendo T3 Code en su PC.
  - Lanzarlo y matarlo desde el mod, protocolo local y reconexión.
- **Auth y tokens.** La cuenta viviría en el almacén del CLI (`cloud-cli-oauth-token` en su `ServerSecretStore`) y el mod no guardaría tokens propios. Pero, como se vio en §2, el token del CLI **no alcanza** para conectar a un entorno.
- **Máquina del jugador.** Un proceso Node/binario, un puerto o stdio, SQLite y puede que `cloudflared`. Es pesado para algo que debería ser «solo leer».
- **Latencia y robustez.** Un salto local extra, despreciable. El ciclo de vida es la parte difícil: huérfanos si Minecraft muere mal, reinicio, versiones del CLI desalineadas.
- **Mantenimiento.** La responsabilidad pasa a upstream, pero el mod queda acoplado al ritmo de lanzamientos del CLI (alfa, 0.1.x) y a un protocolo local que **todavía no existe**.
- **Seguridad.** Buena en la idea (el mod sin secretos, scopes impuestos por el sidecar), pero esos scopes habría que implementarlos allí. El CLI actual no tiene un modo «solo lectura» para el canal local.
- **Esfuerzo en el mod.** Un gestor de sidecar (nuevo `dev.t3craft.t3.sidecar`) y un `T3Connection` delgado en lugar de `Pairing`/`PairingClient`/`Environments`. **XL e incierto**, depende de una función de upstream que no está.
- **Riesgos.** Que upstream no lo construya. Que «no ejecuta nada» no sea posible con `t3 serve`. Una dependencia fuerte de instalación del lado del jugador.

### C) Modo directo actual

- **Qué hace falta.** Nada nuevo. Es el estado del repo.
- **Auth y tokens.** Un Bearer por entorno con `read` (y `operate` si se pidió), de 30 días y sin refresh, en `credentials/` (600).
- **Máquina del jugador.** Solo Minecraft.
- **Latencia y robustez.** La mejor: va directo, sin relay ni túnel. Depende de que la URL sea alcanzable.
- **Mantenimiento.** Solo el protocolo de orquestación v2 (el servidor responde 426 si cambia), que ya seguimos. Es la API documentada de `t3 auth pairing`.
- **Seguridad.** La mejor: scopes mínimos verificados en cada canje y en cada lectura de disco, y un token por entorno revocable en el host.
- **Esfuerzo.** Cero. Mejoras posibles: ver el siguiente apartado.
- **Riesgos.** Fricción de uso y la caducidad a los 30 días.

### Tabla comparativa

| | A) Java nativo | B) CLI sidecar | C) Directo (hoy) |
|---|---|---|---|
| Viable hoy | Solo suplantando a `t3-web` (sin permiso) | No: falta un modo cliente en upstream | Sí |
| Login único con la cuenta | Sí | Sí (en teoría) | No |
| Secretos en el mod | Refresh de cuenta + clave DPoP | Ninguno | Un Bearer acotado por entorno |
| Radio de una fuga | Shell en todas las máquinas | Depende del CLI | Un entorno, solo `read`/`operate` |
| En la PC del jugador | Solo Minecraft | Proceso Node/binario + SQLite | Solo Minecraft |
| Renovación | 1 h automática | Del CLI | 30 días, a mano |
| Alcance de red | Cualquiera, por túnel | Cualquiera, por túnel | La URL debe ser alcanzable |
| Mantenimiento | Alto (Clerk + relay + DPoP) | Medio, acoplado al CLI | Bajo |
| Esfuerzo en el mod | L | XL + función de upstream | 0 |

## 4. Recomendación y camino por fases

**Mantener C como camino principal ahora** y pedir a upstream una vía oficial antes de construir nada. Razones:
- A y B dependen de una decisión de T3 Tools Inc. que no está documentada: aceptar clientes de terceros, o publicar un modo cliente headless.
- A además degrada la seguridad: la credencial de cuenta equivale a `terminal:operate` en todas las máquinas, justo lo que `Pairing` evita hoy.
- B no existe todavía.

Fases:
1. **Ahora (C+, barato).** Comprobar en un entorno real si el emparejamiento directo funciona contra el hostname del túnel de T3 Connect, p. ej. `t3 auth pairing create --base-url https://<hostname-del-túnel>`. Los túneles exponen el origen HTTP del host, así que `/oauth/token` y `/ws` deberían pasar, pero **no está confirmado**. Si funciona, el punto débil de la «URL alcanzable» desaparece sin login de cuenta. Documentarlo en `docs/features/operate.md`/`config.md` y en `PairScreen`.
2. **Consultar a upstream** (decisión de Facundo, ver §5): abrir un issue o discusión en `pingdotgg/t3code` preguntando por (a) clientes de terceros para T3 Connect, con un `client_id` propio y scopes por entorno, y (b) un modo cliente headless del CLI con una interfaz local solo de lectura.
   Borrador (no publicado): `docs/plans/t3-connect-upstream-query.md`.
3. **Solo si upstream abre la puerta.**
   - Si ofrece un `client_id` propio: prototipo de A, empezando por login, `/v1/environments` y DPoP, con `read` por defecto.
   - Si ofrece el modo cliente en el CLI: reabrir B.
   - Entre ambos, preferir A si hay `client_id` propio (sin runtime extra) y B si lo que se ofrece es el binario con modo cliente.
4. **Mejoras independientes en C**: aviso de caducidad de la sesión (30 días) y un flujo de «volver a emparejar» en un clic.

## 5. Preguntas abiertas para Facundo

1. ¿Autorizas contactar a upstream (issue o discusión en `pingdotgg/t3code`)? Es una acción pública, así que no la hice.
2. ¿Aceptas, por ahora, que el mod **no** use el `client_id` `t3-web` de T3 para entrar a la cuenta? Yo no lo recomiendo.
3. ¿Qué pesa más: comodidad (login único) o radio de una fuga (un token por entorno con scopes acotados)? Con T3 Connect la credencial de cuenta equivale a control total de las máquinas.
4. ¿Los jugadores típicos ya tienen T3 Code en su PC? Si lo tienen, quizá se pueda apoyar el mod en ese servidor local. **No confirmado** que un servidor local agregue los shells de entornos remotos; no lo encontré en el código.
5. ¿Cuántas máquinas enlaza una cuenta típica? El límite de 3 máquinas por cuenta afecta a A y B, no a C.

## 6. Qué implica para `bridge/` (congelado)

- Ninguno de los tres caminos usa el bridge. `Mode.BRIDGE` y `client.link` sobreviven solo como una vía opcional que deja ~12 archivos del mod con ramas legadas.
- El bridge fue, en el fondo, un sidecar propio (un proceso Node que habla el protocolo de T3 con los contratos vendorizados y el mod se conecta por WS local). Su costo real fue seguir el protocolo (27.6k líneas vendorizadas). Es la mejor evidencia de lo que costaría mantener A o un B casero.
- **Recomendación para D2:** retirarlo cuando se cierre #1 con la decisión «C ahora». Quitar `Mode.BRIDGE`, `client.link` y la carpeta `bridge/`; el historial de git lo conserva. Se reconsidera solo si el camino final fuera un sidecar *propio*, no el CLI de T3.
- Los contratos vendorizados (`VENDORED.md`) solo sirven de referencia mientras se estudia A. Si se descarta A, tampoco hacen falta.
