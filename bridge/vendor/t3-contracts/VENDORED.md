# Vendored `@t3tools/contracts`

- Upstream: https://github.com/pingdotgg/t3code (`packages/contracts/src`)
- Commit: `25bf84ab07f53f141c20239c63e383823a9d5ea0` (2026-11-07)
- License: MIT, Copyright (c) 2026 T3 Tools Inc. (see `LICENSE` in this folder)
- Upstream package is `private` (not published to npm), so it is copied here.

Changes: none. `*.test.ts` files were left out. Files are byte-identical to upstream at
that commit; do not edit them by hand.

The bridge uses these schemas so it decodes T3 traffic with exactly the same Schemas the T3
server uses (`orchestration.subscribeShell`, `AuthWebSocketTicketResult`, the orchestration
protocol version, ...). The server speaks `ORCHESTRATION_PROTOCOL_VERSION` (currently 2) and
answers 426 `orchestration_protocol_incompatible` when it changes.

## Updating

```bash
git clone --depth 1 https://github.com/pingdotgg/t3code /tmp/t3code
rsync -a --delete --exclude='*.test.ts' /tmp/t3code/packages/contracts/src/ bridge/vendor/t3-contracts/src/
cp /tmp/t3code/LICENSE bridge/vendor/t3-contracts/LICENSE
git -C /tmp/t3code rev-parse HEAD   # put this commit above
cd bridge && pnpm typecheck && pnpm test
```
Keep `effect` in `bridge/package.json` on the same version as upstream's `pnpm-workspace.yaml` catalog.
