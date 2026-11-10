# bridge/ (legacy, frozen)

> **Status: legacy and frozen** (decision D2, approved 2026-11-09, in `docs/plans/code-cleanup.md` §7; issue #7).
> Do not build new work on it. New installs use **direct mode**.

## What it is

A Node 22+ / TypeScript / effect process that connects to every configured T3 Code environment over
authenticated WebSocket RPC (one token per environment, using the vendored T3 contracts in `vendor/`). It serves the
mod over a local WebSocket using the t3craft protocol v1 (`../protocol/protocol.md`, types in `src/protocol.ts`).
In this setup the mod draws what the bridge sends and holds no T3 credentials.

The mod only uses it in the legacy `"mode": "bridge"` (`config/t3craft.json`). An older config that has `bridgeUrl`
and no `mode` also stays in bridge mode. In the mod, the bridge path is `dev.t3craft.client.link`
(`BridgeLink`, `BridgeListener`, `Backoff`, `LinkStatus`), plus the `ModConfig.Mode.BRIDGE` branches in about
12 files.

## What replaces it

**Direct mode** (`ModConfig.Mode.DIRECT`, the default since plan v2.1, 2026-11-07). The mod pairs with each T3
environment by itself (`/t3craft pair`), the way the desktop app pairs with a remote server
(`dev.t3craft.t3.DirectController`, `dev.t3craft.t3.T3Connection`). Setup is in the root `README.md`
("Direct mode") and the design is in `docs/plans/plan-v2.md`. Newer features (chat history, lineage, helpers,
leaders, schedule board, queue, panel chips) work only in direct mode. In bridge mode they are missing or degraded,
as each feature doc says.

## Why it is frozen

- None of the paths under study for the long-term connection uses it (`docs/plans/t3-connect.md`, §6, issue #1):
  A is a native Java client, B is the T3 Code CLI as a sidecar, C is the current direct mode.
- Keeping it means following T3's protocol by hand: 27.6k vendored lines against 1.7k of its own.
- `protocol.md` has drifted from `src/protocol.ts`, which also declares types that nothing uses. The mod only sends
  `hello` to it, and the bridge answers every other mod message with "not implemented".

## What is allowed

- **Critical fixes only:** security (for example the listen-address or token handling), or a break that stops an
  existing bridge install from starting or connecting. Keep each fix minimal and say in the commit that it is a
  frozen-bridge fix.
- Doc corrections that describe the current state.

## What is not allowed

- New features, new protocol messages or fields, or new `Mode.BRIDGE` branches in the mod. New mod features target
  direct mode only. In bridge mode they degrade quietly (empty state or "not available"), as they do today.
- Updating the vendored contracts (`vendor/`), upgrading dependencies, or refactors, unless a critical fix needs them.
- Bringing `protocol.md` back in sync with `src/protocol.ts`. The drift stays documented and is not fixed.

## Retirement

Whether it is retired is decided when #1 (T3 Connect) closes. The recommendation in `docs/plans/t3-connect.md` §6
is to retire it if #1 ends with "C now": remove `Mode.BRIDGE`, `dev.t3craft.client.link` and this folder, and let
git history keep them. Until then nothing is moved or deleted.

## Running it (legacy installs only)

The commands, config keys and the systemd unit (`deploy/t3craft-bridge.service`) are in the root `README.md`,
section "Bridge (legacy, frozen)".
