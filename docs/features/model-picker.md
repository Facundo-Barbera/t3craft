# Selector de modelo del composer (issue #21, fase 2a / T1 del plan de UI)

Reescritura del popover del chip de modelo (`t3ui/actions/ModelPicker.java`). El chip cerrado no cambia
(`ComposerSkin`); el popover se hace con el kit (`T3Field`, `T3List`, `T3Select`, `T3Toggle`, `T3Button`) y
solo con tokens de `ChatTheme` (`ChatTheme.warmStudio()`, así que F3+T lo reestiliza).

## Por qué solo salía "Supervised"
`chipRow` dibujaba una fila de chips y hacía `break` en el primero que no cabía; con el popover estrecho solo
entraba el primer modo. Las filas de chips desaparecen: Access es un `T3Select` con todos los
`supportedRuntimeModes` del proveedor.

## Qué hay en el popover
- **Buscador** arriba (`T3Field`). Cada palabra debe aparecer en nombre, nombre corto, slug, `aliases`,
  `subProvider` o `badge`. Enter elige el primer resultado.
- **Proveedores** (izquierda) y **modelos** (derecha), dos `T3List`: las dos hacen scroll (ya no se corta a 10).
- **Modelos**: favoritos primero (cuadrado brass hasta que el kit tenga iconos; clic en el cuadrado de la fila
  los marca), el resto en el orden del servidor, y una fila "Show legacy (N)" al final con los `isLegacy`
  (un favorito o el modelo en uso nunca se esconden). Insignia (`badge`, o "custom" si `isCustom`) y
  `subProvider` en la fila. La selección es relleno `selected` + texto `accent` (sin check Unicode).
- **Reasoning**: `T3Select` del descriptor `select` con `role: "reasoning"`; si no hay, el primer select.
  Cada select extra tiene su `T3Select`; cada descriptor `boolean` (fast, thinking...) es un `T3Toggle`
  debajo. Las selecciones salen en `modelSelection.options` como `ProviderOptionSelection` (`id`, `value`;
  `value` es texto o booleano). Al cambiar de modelo se conserva cada opción que el nuevo modelo también
  ofrece con un valor válido; las demás vuelven al valor por defecto del modelo.
- **Access**: `T3Select` con todos los modos que el proveedor soporta; la descripción de `Modes.Runtime` va
  en el tooltip del select.
- Se quitó la fila Mode (Chat/Plan). `Modes.Interaction` y `ThreadCommands` no cambian: el plan sigue en el
  `ModelPick` y el chip muestra "· Plan".
- En modo hilo, pie con Cancel / Apply (Apply envía solo los comandos que cambiaron, como antes).

## Favoritos
`config/t3craft/model-favorites.json`, un arreglo de claves `envId/instanceId/slug`. Lo lee y escribe
`config/FavoritesStore`, que también usa ahora el piece browser (`piece-favorites.json`, mismo formato).

## Datos
- `ProviderCatalog.Model` lee `isLegacy`, `badge`, `aliases`, `subProvider`, `isCustom` y todos los
  `optionDescriptors` (`select` y `boolean`, con `role` y `description`).
- `ModelPick` guarda `options` (lista de `ProviderOptionSelection`) en lugar de `effortId/effortValue`.

## Probarlo
`/t3dev ui model-picker` (modo hilo nuevo) y `/t3dev ui model-picker-thread` (con Apply / Cancel; Apply apunta
al entorno falso y falla con el toast habitual). Catálogo falso con 13 proveedores, modelos legacy, un modelo
custom, un proveedor sin Auto y descriptores de todos los tipos; los favoritos viven en memoria.
