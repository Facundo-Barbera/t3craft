# feat/20-outbound-events: eventos salientes (webhooks)

Issue #20. Plan: `docs/plans/ui-browser-and-integrations.md`, frente D. Investigación:
`docs/research/game-ui-and-integrations.md`, I3, I4 y §4.

> ## ⚠️ Qué sale de tu ordenador
>
> Esta función **manda datos fuera de la máquina**, a las URL que tú configures (un servicio de notificaciones,
> un canal de chat, tu Home Assistant…). Por eso está **apagada por defecto** y se activa en dos pasos: el
> interruptor general y, además, cada destino.
>
> Según el destino, un mensaje puede llevar: el **título del hilo**, el **proyecto**, el **id del entorno**
> (`my-vps`), el **estado** (necesita tu atención, falló, terminó) y el texto de la actividad ("Approve command?").
> Los servicios que los reciben (ntfy.sh público, Discord, Slack…) pueden guardarlos y los ve quien tenga acceso a
> ese tema o canal.
>
> - Para **no mandar ningún contenido**, deja activado **"Pending only"** (es el valor al crear un destino): el
>   mensaje es siempre "Something is pending in T3.", sin títulos, proyectos, entornos ni texto.
> - Un tema de ntfy.sh público es "privado" solo por su nombre: usa un tema largo e impredecible o autohospeda.
> - Con `http://` (sin TLS) todo viaja sin cifrar. Solo tiene sentido en tu red local.
> - Solo funciona **con el juego abierto**. Para avisos con el juego cerrado hace falta un proceso aparte
>   (issue #1); la pieza de eventos ya está separada para poder reutilizarla.

## Cómo se usa

Ajustes (tecla **J** o Mod Menu) → botón **Outbound…** (arriba a la izquierda).

1. **Add destination**: crea un destino (por defecto: plantilla Push, eventos *Needs your input / Thread failed /
   Thread finished*, "Pending only" activado).
2. En el destino: nombre, **plantilla**, **URL del webhook**, acción al tocar (solo Push), "Pending only" y los
   eventos que recibe. **Save** guarda; **Send test** manda un mensaje de prueba (con los ajustes ya guardados);
   **Delete** borra el destino y su URL.
3. Vuelve a la lista y enciende **Send events outside the game**: pide confirmación con el aviso de arriba.

## Eventos

| Evento | Id (`events[]`) | Cuándo | Hoy lo emite el juego |
|---|---|---|---|
| Needs your input | `needs_attention` | el hilo espera una respuesta o aprobación | sí |
| Thread failed | `error` | el hilo falló | sí |
| Thread finished | `done` | el hilo terminó (tras trabajar o esperar) | sí |
| New thread | `new_thread` | aparece un hilo nuevo | no todavía |
| Run finished | `run_finished` | termina un run | no todavía |

El modelo (`dev.t3craft.outbound.OutboundEvent`) ya incluye los cinco tipos y los filtros los aceptan; el juego solo
engancha los tres primeros, los que `Notifier.transition` ya anuncia. Los otros dos se añaden con una llamada a
`OutboundClient`/`OutboundEmitter.emit` desde donde se detecten.

Campos de un evento: tipo, entorno, id del hilo, título del hilo, proyecto, estado, texto y hora.

## Plantillas

- **Push** (estilo ntfy): `POST` con cuerpo de texto y cabeceras `Title`, `Priority` (1 a 5), `Tags` y `Click`
  (acción al tocar; se rellena `{environment}` y `{thread}`, p. ej. `t3code://threads/{environment}/{thread}`).
  Los títulos con acentos o emojis van codificados (RFC 2047). Sin botones.
- **Chat**: texto más bloques enriquecidos, **sin botones** (los webhooks que no son de una aplicación no los admiten).
  Formato Slack si el host acaba en `slack.com`; formato Discord en cualquier otro caso (`content` + hasta 10
  `embeds`, y `allowed_mentions` vacío para que un título con `@everyone` no avise a nadie).
- **JSON**: un documento JSON con los eventos (`source`, `title`, `body`, `pendingOnly`, `events[]`) para Home
  Assistant, n8n, IFTTT…

## Dónde se guarda

| Qué | Dónde | Secreto |
|---|---|---|
| Interruptor general, destinos, plantilla, filtros, "pending only", acción al tocar | `config/t3craft/outbound.json` | no |
| **URL del webhook** de cada destino | `config/t3craft/credentials/outbound/<id del destino>.json` (`{"url": "..."}`) | **sí** |

La URL es un secreto (suele llevar el token dentro): se guarda como credencial, igual que los tokens de los entornos
(`credentials/<env>.json`): permisos solo para el dueño donde el sistema los admite, escritura atómica.

- Nunca está en `outbound.json` (una clave `url` escrita a mano ahí se ignora) ni en `t3craft.json`.
- Nunca se muestra: la pantalla pide la URL en una caja vacía y solo dice "set / no URL".
- Nunca va al log: los mensajes solo llevan el id del destino, códigos HTTP y nombres de clase de excepción (no su
  mensaje, que en algunos errores del JDK incluye la URL).
- Borrar el destino borra su fichero de credencial.

Ejemplo de `outbound.json`:

```json
{
  "schemaVersion": 1,
  "enabled": false,
  "destinations": [
    {
      "id": "dest-1",
      "name": "Phone",
      "template": "push",
      "enabled": true,
      "events": ["needs_attention", "error", "done"],
      "pendingOnly": true,
      "click": "t3code://threads/{environment}/{thread}"
    }
  ]
}
```

Un fichero ausente, roto o con campos inválidos se lee como **apagado**. Máximo 8 destinos.

## Cómo envía

`dev.t3craft.outbound.OutboundEmitter` (Java puro, sin clases de Minecraft, solo `java.net.http.HttpClient`):

- **Fuera del hilo del juego.** `emit` solo pasa el evento a un hilo propio (daemon `t3craft-outbound`) y vuelve. Todo lo
  demás (leer ajustes, leer la URL, montar el mensaje, el `POST`) pasa allí. Tiempo de conexión 5 s, de petición 10 s,
  sin seguir redirecciones. Cualquier fallo se queda en el hilo de envío: el cliente no se entera.
- **Agrupar y espaciar.** Por destino, los eventos del mismo hilo se sustituyen entre sí mientras esperan: un hilo que
  oscila de estado manda solo el último, 5 s después del último cambio (nunca más de 30 s después del primero). Lo que
  vence a la vez sale en **un** mensaje ("3 T3 updates"), y dos envíos al mismo destino van separados al menos 3 s.
  Hasta 50 hilos esperando por destino; si se llena se descarta el más antiguo.
- **`Retry-After`.** Un `429` o `503` con `Retry-After` (segundos o fecha HTTP) pausa ese destino ese tiempo (máximo
  1 h); un `429` sin cabecera espera 30 s. Timeouts y `5xx` se reintentan con espera creciente (5 s, 30 s) hasta 3
  intentos; otros `4xx` descartan el mensaje (con una línea de log, sin URL). Lo que lleva más de 10 min esperando se
  descarta.
- **Apagar es inmediato.** Se lee `outbound.json` en cada evento (con caché por fecha y tamaño): apagar el
  interruptor vacía las colas y no vuelve a enviar nada, sin reiniciar.

## Código

- Pieza pura (`mod/src/main/java/dev/t3craft/outbound/`): `OutboundEvent`, `EventType`, `Destination`, `Template`,
  `Message`, `OutboundConfig`, `OutboundStore`, `OutboundEmitter`, `Transport` + `HttpTransport`, `RetryAfter`.
  No importa nada de Minecraft ni del cliente; sí usa `dev.t3craft.t3.Json` (Gson). El issue #1 puede usarla desde
  otro proceso con su propia fuente de eventos.
- Cliente (`mod/src/client/java/dev/t3craft/client/`):
  - `outbound/OutboundClient`: convierte lo que anuncia `Notifier.transition` en un `OutboundEvent` (entorno e hilo
    salen de `npcId` = `<entorno>:<hilo>`; título y proyecto del texto `"<hilo> · <proyecto> (<entorno>)"`).
  - `hud/Notifier.java`: **una** línea al inicio de `transition(...)` llama a `OutboundClient.onTransition`.
  - `config/OutboundScreen` y `config/OutboundDestinationScreen`; `config/SettingsScreen` tiene un botón más
    ("Outbound…").
- Los ajustes viven en su propio fichero, no en `T3CraftSettings`, para no tocar el record compartido ni el
  `ConfigCodec`; son aditivos y un `t3craft.json` antiguo no cambia.

## Límites conocidos

- Sin el juego abierto no hay avisos (issue #1).
- `new_thread` y `run_finished` existen en el modelo pero el juego aún no los emite.
- El proyecto y el título se sacan del texto que ya compone el juego; si un día cambia su formato, se envía el texto
  entero como título y sin proyecto.
- Solo POST. No hay botones de acción en ninguna plantilla.
- No probado dentro del juego ni contra servicios reales (regla: no lanzar Minecraft ni compilar en el agente).
