# Leader office: what AgentCraft has that t3craft lacks, and the blocks it asks for

Branch `struct/leader-office` (2026-11-09). The office tower (`citadel/CitadelOffice.java`) uses only blocks and bindings
that already exist. This note lists what is still missing, as code to review. **Nothing here is wired in:** every block
below touches the kernel (`dev.t3craft.blocks.*`, `AnchorNames`, `HqBindings`), so it needs a daemon restart and a
`RESTART` entry when integrated.

## Review of AgentCraft (https://github.com/blendi-remade/agentcraft, MIT)

Read from its README and its `mod/` assets. Nothing was copied: the ideas below are our own implementation, so
`THIRD_PARTY_NOTICES.md` already covers it ("design reference"). If any model, texture or code file is ever copied,
list it there first.

| AgentCraft | what it does | t3craft today | gap |
| --- | --- | --- | --- |
| `monitor` | live log of one agent's work, merges into screens | `t3craft:monitor`, big screens (`bigscreen_<key>@WxH=<binding>`) | none |
| `status_lamp` | colour and light by agent state | `status_lamp`, `board_lamp`, `paper_lantern` | none |
| `task_board` | Task Wall kanban | `thread_board` (`THREAD_WALL`) | none; one per plan |
| `memory_archive` / `memory_catalog` | the shared library | `archive_terminal` | one per plan; no catalog view |
| `console_terminal` | Enter opens the command console (`` ` `` key, `@agent msg`, `/status`) | chat/composer screens, no block | **block that opens the composer** |
| `decision_podium` | a clay `!` and bell; the agent walks over and waits; `J` answers questions, permission prompts, merges | attention board (lamps), NPCs gather at `board_slot_*` | **block bound to the most urgent thread that opens it** |
| `merge_station` | diff review screen and the Merge button | none | **block that opens the thread's pending review/PR** |
| `glow_panel` / `glow_strip` | emissive wall and floor lighting | lanterns only | **decorative emissive kit blocks** |
| agent card (right click), nameplates, speech bubbles, particles | state, task, log, pause/stop | nameplates and NPC labels exist | out of scope for blocks |
| `/status` (goal, agents, spend) | one-line summary | none | a big screen bound `beacon`-style could show it later |

The office already ports the *flow*: threads screen (hub feed) + two project feeds + an attention board that continues
the hub's (lamps `board_lamp_5..9`, ranks 6 to 10) + consoles, archive and workshop. What cannot be done with vanilla
stand-ins is below.

## Proposed blocks

All follow the pattern of `ArchiveTerminalBlock` (facing, shape, client `use` hook set by the client module). Names and ids
below are proposals.

### 1. `console_terminal` (kit-2 → new block `t3craft:console_terminal`)

Click opens the thread composer (`T3Screen` draft) or the chat of the nearest bound thread. Unbound, it opens the
composer for a new thread. The office puts three of them where the lecterns stand now.

```java
public final class ConsoleTerminalBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	@FunctionalInterface public interface ClientUse { void use(BlockPos pos); }
	private static volatile @Nullable ClientUse clientUse;
	public static void setClientUse(@Nullable ClientUse use) { clientUse = use; }

	@Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
		ClientUse u = clientUse;
		if (l.isClientSide() && u != null) { u.use(p); }
		return InteractionResult.SUCCESS;
	}
	// shape: the desk shape of ArchiveTerminalBlock without the screen housing; model: a desk with a slanted keyboard
	// ... newBlockEntity not needed: no state to store
}
```

Registration: `T3Blocks.Content` gains `consoleTerminalBlock` (and `HqBlockFixtures.States#consoleTerminal(Direction)`);
`HqKit.Role` gains `CONSOLE` with a vanilla fallback (lectern). **Kernel: yes** (`T3Blocks`, `HqBlockFixtures`, `HqKit`,
`KitBlocks.IDS`, assets).

### 2. `decision_podium` (`t3craft:decision_podium`)

Bound to `board:<i>` like a board lamp. It lights when the i-th most urgent thread needs the player (reuse `LampStatus`),
and a click opens that thread. A podium row replaces the lamps of the office board and gives the NPC gathering spot a
block to stand next to.

```java
public final class DecisionPodiumBlock extends BaseEntityBlock {
	// block entity: BoundBlockEntity (binding "board:<i>"), same as LampBlockEntity
	// client: LampSignals already resolves board:<i> -> LampStatus; the model swaps by `status` like status_lamp
	// click: BlocksClient.openThread(resolve(pos, binding))
}
```

Anchor side: `AnchorNames.podium(int i)` = `"podium_" + i`, `HqBindings.of` adds `Role.PODIUM` with `Binding.board(i)`.
**Kernel: yes** (`AnchorNames`, `HqBindings`, `HqBlockFixtures`, `T3Blocks`).

### 3. `merge_station` (`t3craft:merge_station`)

Bound to a project feed (`project:<key>`); click lists the threads of that project with pending review and opens the
chosen one's pull request in the browser (or the diff screen when one exists). Lit while a review is pending. Only worth
it when the T3 side exposes review state: until then keep the project screens.
**Kernel: yes.**

### 4. `glow_strip` / `glow_panel` (kit blocks)

Two emissive decorative blocks (a thin strip along a beam or floor edge, a full panel): light 12, no block entity, in
`KitBlocks.IDS`. They replace the visible light fixtures in floors that the art direction forbids and give interiors
light without hanging lanterns everywhere. Bump the kit id (`kit-3`). **Kernel: yes** (`KitBlocks`).

### 5. A second archive terminal

`AnchorNames.ARCHIVE_TERMINAL` is unique, so the office has lecterns at the archive level. To allow more:

```java
// AnchorNames
public static final String ARCHIVE_TERMINAL_EXTRA = "archiveTerminal@";   // archiveTerminal@<n>
// HqBindings.of: after the single-anchor case
for (Anchor a : plan.anchors().values()) {
	if (a.name().startsWith(AnchorNames.ARCHIVE_TERMINAL_EXTRA)) {
		out.putIfAbsent(a.block(), new Fixture(a.block(), Role.ARCHIVE_TERMINAL, Binding.NONE, a.facing()));
	}
}
```

then `CitadelOffice.archive` replaces the lecterns by `c.fixture(pos, c.style.terminal(facing))` plus
`Anchor.at(ARCHIVE_TERMINAL_EXTRA + n, ...)`. `AnchorNames.standing` must treat the new prefix as a surface anchor.
**Kernel: yes** (`AnchorNames`, `HqBindings`).

## What the office already does without them

- `board_lamp_5..9` are plain anchors: no kernel change. They show ranks 6 to 10 of the attention queue.
- Project feeds on the west wall: `project:<key>` of the two busiest projects (rebuilt with the roster).
- Hub feed (`HqGenerator.hubBinding`) on the main screen.
