# Chips de modelo y rama (ítems 3 y 14 del inventario)

Cada NPC muestra el modelo del hilo y, en el panel, su rama y su worktree. La estación de trabajo muestra la rama bajo la línea de estado.

| Dónde | Por defecto | Ejemplo |
|---|---|---|
| Nameplate | modelo | `⚙ Fix login  [opus 5.5]` |
| Panel | modelo · proveedor, rama · worktree | `◆ opus 5.5 · claude   ⎇ feat/x · repo` |
| Pantalla del escritorio | rama | `⎇ feat/x` |

## Datos

`ThreadMeta.fromJson` lee del shell `providerInstanceId`, `modelSelection` (`instanceId` o el antiguo `provider`, más `model`), `branch` y `worktreePath`. También acepta alias: `modelSelection` como cadena, `model` y `provider` en el nivel superior, `gitBranch` y `worktree`. Si un campo falta, viene vacío o tiene otro tipo, queda en null y no se muestra.

`Chips` (puro) abrevia los valores:

- Modelo: `claude-opus-5-5` → `opus 5.5`; `claude-3-5-sonnet-20241022` → `sonnet 3.5`; `gpt-5.1-codex-max` → `codex 5.1 max`. Si no hay modelo, se usa la etiqueta del proveedor (`claudeAgent` → `claude`; el resto sin cambios). Máximo 16 caracteres.
- Worktree: se queda el último segmento, o `~` si es un home (`/home/u`, `/Users/u`, `/root`, `C:\Users\u`). Se omite en el panel cuando solo repite la rama (`feat/x` ↔ `feat-x`).

## Config (aditiva, `config/t3craft.json`)

```json
"chips": {
  "modelNameplate": true, "modelPanel": true,
  "branchNameplate": false, "branchPanel": true, "branchWorkstation": true
}
```

Si falta una clave o tiene un tipo incorrecto, se usa su valor por defecto. Los cambios se recogen en caliente: el archivo se relee cada 2 s como mucho.

## Modo bridge

`NpcInfo.fromJson` acepta las claves opcionales `model`, `provider`, `branch` y `worktree`. El bridge todavía no las envía, y sin ellas no se muestra nada.
