# Borrador: consulta a upstream sobre T3 Connect

> **Borrador, no publicado.** Es el texto para que Facundo lo publique como issue o discusión en
> `pingdotgg/t3code` (issue #1; contexto en `docs/plans/t3-connect.md`, §2, §4 paso 2 y §5).
> Ningún agente publica nada. Decisiones ya tomadas: el mod **no** usará el `client_id` oficial
> `t3-web` / `t3-mobile`, y el emparejamiento directo actual sigue como camino principal.
> El texto va en inglés porque es el idioma de upstream.

---

**Title:** Question: supported path for third-party clients of T3 Connect (read-only, least privilege)?

Hi, and thanks for T3 Code.

I maintain **t3craft**, an open-source Minecraft mod that shows T3 Code threads inside the game. It is read-mostly, with optional "operate" actions the player must enable.

**How it connects today.** For each environment, the player runs `t3 auth pairing create` with narrowed scopes (for example `orchestration:read` only), and the mod exchanges the pairing credential at `/oauth/token`. This works well and gives per-environment, least-privilege access. Its weak point is that the player needs a URL that is reachable from the game machine.

I read through T3 Connect in the public source (`docs/internals/t3-connect.md`, `packages/contracts/src/relay.ts`, `infra/relay/src/`, `apps/server/src/cloud/CloudLink.ts`, at commit 33db45ef). As I understand it, the official clients use a Clerk session, exchange it at `/v1/client/dpop-token` with `client_id` limited to `t3-web` / `t3-mobile`, list environments, and then get a short-lived credential per environment. I would like to build on this instead of asking players to expose URLs, but I do not want to guess.

**To be explicit: t3craft will not reuse the official `t3-web` / `t3-mobile` client_ids.** We do not want to impersonate T3's apps, and we will keep using direct pairing until there is a supported alternative.

Questions:

**(a)** Is there, or will there be, a supported way for third-party clients to use T3 Connect? Specifically: their own registered `client_id`, an account login flow that suits a game client (device grant or loopback PKCE), and per-environment, least-privilege scopes (for example only `orchestration:read`) instead of the full standard client scope set.

**(b)** Would you consider a headless "client mode" of the CLI that logs in, discovers the linked environments, and exposes a local read-only interface (the shell snapshot and event streams) without running any agents? A third-party app could then talk to a local process and never handle account credentials itself.

**(c)** Is direct pairing against the T3 Connect tunnel hostname supported, or expected to work? For example `t3 auth pairing create --base-url https://<tunnel-host>`, followed by `/oauth/token` and the WebSocket against that host. It looks like it should pass through the tunnel, but I could not confirm it from the source.

Any pointer to existing docs, or a "not planned", would already help us decide. Happy to contribute documentation or a small proof of concept if there is interest.

Thanks!
