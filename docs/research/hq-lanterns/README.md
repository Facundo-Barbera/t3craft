# HQ lighting and decor (fix/hq-lanterns)

Feedback de Facundo: "muchos diseños usan stone lanterns o similares pero no los usan del todo bien. A veces estorban o quedan flotando o no quedan bien", y "también falta luz en muchos diseños".

## Qué se valida

`HqDecorValidator` (test, `mod/src/test/java/dev/t3craft/hq/`) analiza el plan real de cada layout. La caminabilidad es la misma que usa la navegación de los NPC (`PlanWalkability`). Detecta:

| Tipo | Regla |
|---|---|
| FLOATING | Linterna colgada sin bloque sólido ni cadena encima (regla `canSupportCenter` de MC); linterna de pie, maceta, vela o antorcha sin suelo; antorcha de pared sin muro; end rod sin anclar; cadena cuyo extremo superior no termina en un bloque. |
| OBSTRUCTS | Decoración en celdas de paso: el pie o la cabeza de los escritorios, las puertas y los anchors de pie; delante de una pantalla 3×2 o de la pared kanban; en la columna del holograma; a la altura de la cabeza sobre una celda caminable; o cortando un paso de 1 de ancho (pasillo, escalera o puerta que solo es caminable sin ella). |
| EXCESS | Más de 4 luces sueltas o más de 8 adornos (sin contar cadenas) en una ventana de 9×9×9. |
| DARK | Celda alcanzable a pie desde el spawn con luz de bloque nocturna < 10. Los tejados a cielo abierto (cuyo suelo es la teja, el remate o la cumbrera del estilo) se cuentan aparte en `roofDim`: no son zona de paso y en esos medios bloques no aparecen mobs. |

La luz se calcula con `HqLightMap` (main): un BFS en memoria a partir de la emisión de cada bloque, que pierde `max(1, dampening)` por paso y se corta en las caras que ocluyen, con la misma regla que el `BlockLightEngine` del juego (`LightEngine.getLightDampeningInto`). No hay luz de cielo, porque representa el HQ a medianoche.

## Arreglos

- **Iluminación integrada (`HqLighting`)**, aplicada al final de `HqGenerator.generate`, el punto común a los 6 layouts y al estilo classic:
  1. una retícula de lámparas empotradas en el suelo cada 5 bloques, en coordenadas del plan, así que es simétrica respecto al hub y estable al crecer, y solo donde hay celdas caminables oscuras;
  2. un relleno voraz para lo que quede: el bloque de suelo, muro o techo cercano que ilumina la celda y el mayor número de celdas oscuras, con puntuación exacta por BFS.

  **Local a cada parte.** El HQ crece y se reduce por grupos (`BuildPlan.groups()`), y los diffs solo deben tocar lo que cambia. Por eso cada grupo se ilumina **por su cuenta**:
  - solo cuentan sus propias lámparas;
  - las celdas de otros grupos se toman como opacas y su luz no suma, así que el HQ real tiene como mínimo la luz estimada;
  - las lámparas solo se empotran en bloques del propio grupo;
  - las celdas de los escritorios (los módulos `_desk_` por encima del suelo, las pantallas y el tablero) se ven como aire tanto si el escritorio está ocupado como libre.

  Así, sumar proyectos, ocupar o liberar un escritorio no mueve ninguna lámpara de las partes que ya existen. Lo cubren `CampusLayoutTest.addingProjectsAndThreadsMovesNothingThatExists`, `HqDiffTest.releasedDeskIsRemoved` y `HqGeneratorTest.goneThreadRemovesItsDesk`.

  Solo cambia bloques opacos completos, cristal de suelo y hojas de copas a las que se puede subir. Nunca toca pantallas, escritorios, la pizarra, la terminal, las lámparas de estado, los bloques con block entity ni nada a lo que apunte un anchor. La caminabilidad, los anchors y los módulos quedan intactos. Lámparas usadas:
  - **cherry:** froglight perlado en el suelo y panel shoji encendido de HqKit en muros y techos (con froglight perlado como fallback vainilla);
  - **classic:** shroomlight.
- **ensanche:** las 6 linternas de los soportales colgaban de la cara inferior de una losa superior, que no las sostiene. Ahora cada una tiene encima un bloque completo de piedra lisa.
- **ring:** las macetas del patio quedan solo en el anillo interior (r ≤ 14,5). En r = 14,9 cortaban el paso junto al tambor.
- **vertical:** no se pone maceta en la logia cuando esta tiene 1 de ancho, es decir, cuando detrás queda el vacío del atrio o el hueco de la escalera (`narrowLoggia`).
- **classic, en cherry y en el estilo classic:** en las alas queda un farol colgado por pilar en el eje de la nave (antes había dos por pilar), y los tōrō y farolas del jardín pasan a estar en los pilares alternos. La luz la ponen los paneles shoji encendidos y las lámparas empotradas.

## Incidencias antes → después (6 layouts con cherry, más el estilo classic)

"Antes" es main (4ba5183) medido con el validador. "Después" es la última pasada local de la primera versión, con la iluminación global. La versión por grupo pone más lámparas porque cada parte se ilumina sola, así que todos los valores finales los mide integration (TBD).

| Layout | n | Decoración antes (F/O/E) | Decoración después | Celdas oscuras antes | Después (última pasada) | Luces antes → después |
|---|---|---|---|---|---|---|
| classic | 4 | 0/0/35 | TBD (esperado 0) | 477 / 1084 | 0 | 122 → 178 |
| classic | 10 | 0/0/107 | TBD | 1030 / 2354 | 0 | 298 → 422 |
| classic | 20 | 0/0/224 | TBD | 1909 / 4366 | 0 | 583 → 804 |
| campus | 4 | 0/0/0 | 0 | 15089 / 19156 | 0 | 435 → 1435 |
| campus | 10 | 0/0/0 | 0 | 27933 / 35317 | 0 | 794 → 2614 |
| campus | 20 | 0/0/0 | 0 | 50670 / 63686 | 0 | 1396 → 4675 |
| ring | 4 | 0/2/0 | TBD (esperado 0) | 18170 / 22532 | 0 | 1068 → 2108 |
| ring | 10 | 0/2/0 | TBD | 32432 / 43366 | 0 | 2027 → 3942 |
| ring | 20 | 0/2/0 | TBD | 33137 / 44047 | 0 | 2522 → 4454 |
| vertical | 4 | 0/2/2 | TBD | 6114 / 9898 | 0 | 563 → 1011 |
| vertical | 10 | 0/4/2 | TBD | 8961 / 16972 | 0 | 1155 → 1938 |
| vertical | 20 | 0/10/2 | TBD | 11101 / 21470 | 5 (tejado, TBD con roofDim) | 1671 → 2646 |
| ensanche | 4 | 6/0/0 | TBD (esperado 0) | 9834 / 13206 | 0 | 193 → 775 |
| ensanche | 10 | 6/0/0 | TBD | 15206 / 21610 | 0 | 361 → 1317 |
| ensanche | 20 | 6/0/0 | TBD | 20757 / 31501 | 0 | 611 → 2022 |
| village | 4 | 0/0/0 | 0 | 6717 / 8083 | 95 (aleros a ras de terraza → roofDim) | 106 → 524 |
| village | 10 | 0/0/0 | 0 | 8708 / 10963 | 0 | 172 → 747 |
| village | 20 | 0/0/0 | 0 | 14693 / 19527 | 0 | 306 → 1303 |
| estilo classic | 4/10/20 | 0/0/43, 0/0/131, 0/0/274 | TBD | ≈ 0 (2 a 3 celdas) | 0 | 117 → 114 … |

F/O/E = flotantes, obstruyen, exceso (el exceso se midió antes con el umbral inicial de 6 adornos en 9×9; el umbral definitivo es el de la tabla de reglas). Celdas oscuras = oscuras / caminables.

## Renders (los ejecuta integration)

Antes, desde main (4ba5183), y después, desde esta rama:

```
cd mod
./gradlew hqRender -PhqArgs="--layout all --projects 4,20 --out ../docs/research/hq-lanterns/before"   # en main
./gradlew hqRender -PhqArgs="--layout all --projects 4,20 --out ../docs/research/hq-lanterns/after"    # en fix/hq-lanterns
./gradlew hqRender -PhqArgs="--layout ensanche --projects 4 --only plan --out ../docs/research/hq-lanterns/after"  # soportales
./gradlew hqRender -PhqArgs="--style classic --only full --out ../docs/research/hq-lanterns/after-classic-style"
```

Las zonas a mirar son las siguientes. El corte `_plan` a y = 3 muestra las lámparas empotradas.
- Soportales de ensanche.
- Patio de ring.
- Logias de vertical.
- Alas de classic.
- Tejados de village.

## Tests

`./gradlew test --tests '*HqLightingTest*'` (y `'*Hq*'` para el resto).
