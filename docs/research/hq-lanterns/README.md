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

  **Local a cada parte.** El HQ crece y se reduce por grupos (`BuildPlan.groups()`), y los diffs solo deben tocar lo que cambia. Por eso cada grupo se ilumina **por su cuenta y con margen**:
  - solo cuentan sus propias lámparas;
  - su vista de la luz depende únicamente del grupo:
    - sus celdas cuentan tal como son;
    - el aire sobre sus propios suelos, hasta 3 bloques (donde camina su gente), cuenta como aire;
    - todo lo demás cuenta como opaco, exista o no el vecino.

    Así, un edificio nuevo al lado no cambia sus lámparas, y el HQ real tiene como mínimo la luz estimada;
  - cada celda caminable pertenece al grupo de su suelo (o al de la celda de los pies si esta tiene escalón, losa o alfombra), y sus lámparas van en bloques de ese mismo grupo;
  - en los módulos de escritorio de classic, la fila del escritorio (cajón, tablero y pantalla) es opaca para la luz y la fila donde se está de pie es aire, tanto si el escritorio está ocupado como libre.

  - **grupos dispersos (grounds y barrios de campus, que crecen con el roster):**
    - tienen una retícula fija en coordenadas absolutas, `(x − 2z) mod 9 == 0` sobre su suelo abierto, con una lámpara cada 9 en cada fila y cada columna; no depende de la extensión ni de los vecinos;
    - su relleno ilumina todo lo que se puede pisar sobre sus bloques, accesible o no, para que un camino nuevo que vuelva accesible una celda vieja no cambie nada;
  - **fila de un escritorio libre** (opaca en la estimación): se le exige ≥ 11 a la celda de pie que tiene delante;
  - **último recurso:** si ningún bloque propio llega a una celda (una escalera que sube por el hueco del forjado de otra parte), la lámpara va en el bloque vecino que mejor la ilumine.

  Así, sumar proyectos, ocupar o liberar un escritorio no mueve ninguna lámpara de las partes que ya existen. Lo cubren `CampusLayoutTest.addingProjectsAndThreadsMovesNothingThatExists`, `HqDiffTest.releasedDeskIsRemoved` y `HqGeneratorTest.goneThreadRemovesItsDesk`.

  Solo cambia bloques opacos completos, cristal de suelo y hojas de copas a las que se puede subir. Nunca toca pantallas, escritorios, la pizarra (los bloques de su cara junto al anchor `board`), la terminal, las lámparas de estado, los bloques con block entity ni nada a lo que apunte un anchor. La caminabilidad, los anchors y los módulos quedan intactos. Lámparas usadas:
  - **cherry:** froglight perlado en el suelo y panel shoji encendido de HqKit en muros y techos (con froglight perlado como fallback vainilla);
  - **classic:** shroomlight.
- **ensanche:** las 6 linternas de los soportales colgaban de la cara inferior de una losa superior, que no las sostiene. Ahora cada una tiene encima un bloque completo de piedra lisa.
- **ring:** las macetas del patio quedan solo en el anillo interior (r ≤ 14,5). En r = 14,9 cortaban el paso junto al tambor.
- **vertical:**
  - no se pone maceta en la logia cuando esta tiene 1 de ancho, es decir, cuando detrás queda el vacío del atrio o el hueco de la escalera (`narrowLoggia`);
  - en las alas con plan CELLS, la maceta del extremo norte (lz = −13), que tapaba el paso junto a la primera oficina, ya no se pone; queda la del sur.
- **ring:** los faroles colgados de la bóveda del anillo 1 seguían `(7x + 11z) mod 17`, que los alinea en diagonal cada ~2 bloques (5 luces sueltas en 9×9×9). Ahora siguen una rejilla de 6×6 (como mucho 4 en 9×9) y solo donde la bóveda tiene altura (h ≥ 3), no en el hueco bajo su arranque.
- **estilo classic:** el hub ya no lleva los faroles de pedestal junto a la puerta sur, porque los de los postes del pórtico ya enmarcan la entrada (antes eran 5 luces sueltas en 9×9×9 alrededor de (0,4,11)).
- **classic, en cherry y en el estilo classic:** en las alas queda un farol colgado por pilar en el eje de la nave (antes había dos por pilar), y los tōrō y farolas del jardín pasan a estar en los pilares alternos. La luz la ponen los paneles shoji encendidos y las lámparas empotradas.

## Incidencias antes → después (6 layouts con cherry, más el estilo classic)

"Antes" es main (4ba5183), medido con el validador. "Después" es esta rama, medida con el mismo validador sobre los planes reales (harness en memoria, sin gradle, con `HqTestSupport.boot()`, `HqGenerator.plan` y `HqDecorValidator.check`).

| Layout | n | Decoración antes (F/O/E) | Decoración después | Celdas oscuras antes | Celdas oscuras después | Luces antes → después |
|---|---|---|---|---|---|---|
| classic | 4 | 0/0/35 | 0/0/0 | 477 / 1084 | 0 | 122 → 163 |
| classic | 10 | 0/0/107 | 0/0/0 | 1030 / 2354 | 0 | 298 → 382 |
| classic | 20 | 0/0/224 | 0/0/0 | 1909 / 4366 | 0 | 583 → 732 |
| campus | 4 | 0/0/0 | 0/0/0 | 15089 / 19156 | 0 | 435 → 2934 |
| campus | 10 | 0/0/0 | 0/0/0 | 27933 / 35317 | 0 | 794 → 5054 |
| campus | 20 | 0/0/0 | 0/0/0 | 50670 / 63686 | 0 | 1396 → 8997 |
| ring | 4 | 0/2/0 | 0/0/0 | 18170 / 22532 | 0 | 1068 → 2139 |
| ring | 10 | 0/2/0 | 0/0/0 | 32432 / 43366 | 0 | 2027 → 3947 |
| ring | 20 | 0/2/0 | 0/0/0 | 33137 / 44047 | 0 | 2522 → 4480 |
| vertical | 4 | 0/2/2 | 0/0/0 | 6114 / 9898 | 0 | 563 → 1045 |
| vertical | 10 | 0/4/2 | 0/0/0 | 8961 / 16972 | 0 | 1155 → 1994 |
| vertical | 20 | 0/10/2 | 0/0/0 | 11101 / 21470 | 0 | 1671 → 2708 |
| ensanche | 4 | 6/0/0 | 0/0/0 | 9834 / 13206 | 0 | 193 → 781 |
| ensanche | 10 | 6/0/0 | 0/0/0 | 15206 / 21610 | 0 | 361 → 1341 |
| ensanche | 20 | 6/0/0 | 0/0/0 | 20757 / 31501 | 0 | 611 → 2043 |
| village | 4 | 0/0/0 | 0/0/0 | 6717 / 8083 | 0 (80 en tejados, roofDim) | 106 → 565 |
| village | 10 | 0/0/0 | 0/0/0 | 8708 / 10963 | 0 | 172 → 792 |
| village | 20 | 0/0/0 | 0/0/0 | 14693 / 19527 | 0 | 306 → 1378 |
| estilo classic | 4 | 0/0/43 | 0/0/0 | ≈ 0 | 0 | 117 → 97 |
| estilo classic | 10 | 0/0/131 | 0/0/0 | ≈ 0 | 0 | 266 → 213 |
| estilo classic | 20 | 0/0/274 | 0/0/0 | ≈ 0 | 0 | 496 → 399 |

- F/O/E = flotantes, obstruyen, exceso. En "antes", el exceso se midió con el umbral inicial (6 adornos en 9×9); el umbral definitivo es el de la tabla de reglas.
- Celdas oscuras = oscuras / caminables.
- Luces = bloques que emiten luz, incluidas las lámparas empotradas. En el estilo classic bajan porque las alas pierden la mitad de los faroles sueltos y las lámparas empotradas los sustituyen.

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
