package dev.t3craft.hq.style;

import dev.t3craft.hq.HqFixtures;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The HQ's decorative kit by <b>role</b> (desk, wall panel, floor, beam, lantern, plaque, railing,
 * garden), so an {@code HqStyle} (e.g. the cherry campus, style C) asks for "a wall panel" and gets
 * t3craft's own block when the blocks module is on, or a vanilla stand-in when it is off.
 *
 * <p>Same pattern as {@link HqFixtures}: the hq package only knows this interface and its
 * {@link #VANILLA} fallback; {@code dev.t3craft.blocks} installs its implementation with
 * {@link #use} from {@code T3Blocks.init}, and only when the module is on. Roles and fallbacks are
 * documented in {@code docs/features/blocks.md}.
 *
 * <p>Contract for implementations: pure (same arguments → equal states), no world access, a state
 * for every role (never null), and the same "shape class" as the vanilla fallback (a full block
 * stays a full block, so walkability and headroom checks hold in both).
 */
public interface HqKit {
	/** The roles. {@link #id()} is the lowercase name (docs, tests, commands). */
	enum Role {
		/** Standing desk the agent works at (no chair). Monitor goes on the cell above. Oriented: front. */
		DESK,
		/** Shoji wall panel, unlit. */
		WALL_PANEL,
		/** Shoji wall panel, lit from behind (emits light). */
		WALL_PANEL_LIT,
		/** Main floor tile (cherry boards with border). */
		FLOOR,
		/** Accent floor tile (polished stone with the t3craft mark): thresholds, hub, paths. */
		FLOOR_ACCENT,
		/** Timber beam or column. Oriented: axis = facing's axis (UP/DOWN → vertical). */
		BEAM,
		/** Cap (finial) on top of a column. */
		BEAM_CAP,
		/** Free-standing paper lantern (emits light; can breathe with a thread's state when bound). */
		LANTERN,
		/** Room plaque on a wall, text from {@link #plaqueData}. Oriented: front. */
		PLAQUE,
		/** Railing / handrail along one edge of the cell. Oriented: the edge it stands on. */
		RAILING,
		/** Garden: potted cherry. */
		GARDEN_PLANTER,
		/** Garden: stone lantern (emits light). */
		GARDEN_LANTERN,
		/**
		 * Glow strip (kit/glow-panel): a thin bar of light along a skirting, a step edge or the foot of a beam. Oriented:
		 * the way it looks (horizontal; the edge it hugs is behind it). The bottom of the cell, warm (paper/amber).
		 */
		GLOW_STRIP,
		/** Glow strip, bottom of the cell, cool (T3 cyan). */
		GLOW_STRIP_COOL,
		/** Glow strip, top of the cell (under a screen, a beam or a ledge), warm. */
		GLOW_STRIP_HIGH,
		/** Glow strip, top of the cell, cool. */
		GLOW_STRIP_HIGH_COOL,
		/**
		 * Glow panel (kit/glow-panel): a full block that is a light, flush in a ceiling or wall, warm. Oriented: the
		 * face that glows ({@code DOWN} for a ceiling panel, towards the room for a wall one). Fallback: a froglight.
		 */
		GLOW_PANEL,
		/** Glow panel, cool (T3 cyan). */
		GLOW_PANEL_COOL,
		/** Console terminal (kit/console-terminal): opens the T3 composer, or the chat of its bound thread. Oriented: front. Vanilla: lectern. */
		CONSOLE,
		/** Merge station (kit/merge-station): lists the pending pull requests of its project and opens one. Oriented: panel. Vanilla: smithing table. */
		MERGE_STATION;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Whether {@link #state(Role, Direction)} looks at {@code facing}. */
		public boolean oriented() {
			return this == DESK || this == BEAM || this == PLAQUE || this == RAILING || this == CONSOLE || this == MERGE_STATION || this == GLOW_PANEL || this == GLOW_PANEL_COOL
					|| strip();
		}

		/** One of the four glow strip roles. */
		public boolean strip() {
			return this == GLOW_STRIP || this == GLOW_STRIP_COOL || this == GLOW_STRIP_HIGH || this == GLOW_STRIP_HIGH_COOL;
		}

		/** A glow strip role at the top of its cell. */
		public boolean high() {
			return this == GLOW_STRIP_HIGH || this == GLOW_STRIP_HIGH_COOL;
		}

		/** A glow role in the cool (cyan) tone. */
		public boolean cool() {
			return this == GLOW_STRIP_COOL || this == GLOW_STRIP_HIGH_COOL || this == GLOW_PANEL_COOL;
		}
	}

	/** Vanilla stand-ins (blocks module off). */
	HqKit VANILLA = new VanillaHqKit();

	/**
	 * The state for {@code role}. {@code facing}: the front of a desk or plaque, the edge of a railing,
	 * the axis of a beam ({@code UP}/{@code DOWN} = vertical); ignored by the other roles.
	 */
	BlockState state(Role role, Direction facing);

	/**
	 * Block entity data for a {@link Role#PLAQUE} placed by this kit: up to four lines (project, module
	 * number, environment...), the first one in {@code accent}. Always a fresh tag.
	 */
	CompoundTag plaqueData(DyeColor accent, String... lines);

	/** True when the states are t3craft's own blocks (false for {@link #VANILLA}). */
	default boolean custom() {
		return true;
	}

	/** Short stable name for build hashes (bump when any state changes). */
	default String id() {
		return "custom";
	}

	default BlockState state(Role role) {
		return state(role, role == Role.BEAM ? Direction.UP : Direction.NORTH);
	}

	default BlockState desk(Direction front) {
		return state(Role.DESK, front);
	}

	default BlockState wallPanel(boolean lit) {
		return state(lit ? Role.WALL_PANEL_LIT : Role.WALL_PANEL);
	}

	default BlockState floor() {
		return state(Role.FLOOR);
	}

	default BlockState floorAccent() {
		return state(Role.FLOOR_ACCENT);
	}

	default BlockState beam(Direction.Axis axis) {
		return state(Role.BEAM, switch (axis) {
			case X -> Direction.EAST;
			case Y -> Direction.UP;
			case Z -> Direction.SOUTH;
		});
	}

	default BlockState beamCap() {
		return state(Role.BEAM_CAP);
	}

	default BlockState lantern() {
		return state(Role.LANTERN);
	}

	default BlockState plaque(Direction front) {
		return state(Role.PLAQUE, front);
	}

	default BlockState railing(Direction edge) {
		return state(Role.RAILING, edge);
	}

	default BlockState gardenPlanter() {
		return state(Role.GARDEN_PLANTER);
	}

	default BlockState gardenLantern() {
		return state(Role.GARDEN_LANTERN);
	}

	/**
	 * A glow strip (light 12, no collision) looking {@code facing} (horizontal; the wall or edge it hugs is behind it),
	 * at the bottom ({@code high = false}: skirting, step edge) or the top (under a screen or a beam) of its cell, warm
	 * or {@code cool}. Strips in a line join into one continuous bar. Without the blocks module: the invisible
	 * {@code minecraft:light} of the same level, so the cell still lights and still walks.
	 */
	default BlockState glowStrip(Direction facing, boolean high, boolean cool) {
		return state(high ? (cool ? Role.GLOW_STRIP_HIGH_COOL : Role.GLOW_STRIP_HIGH) : (cool ? Role.GLOW_STRIP_COOL : Role.GLOW_STRIP), facing);
	}

	/**
	 * A glow panel (light 12, a full block) whose lit face looks {@code facing}: {@code DOWN} in a ceiling, the room's
	 * direction in a wall. Warm or {@code cool}. Without the blocks module: a froglight (warm) or a sea lantern (cool).
	 */
	default BlockState glowPanel(Direction facing, boolean cool) {
		return state(cool ? Role.GLOW_PANEL_COOL : Role.GLOW_PANEL, facing);
	}

	/** The console terminal, front (keyboard side, where the player stands) towards {@code front}. */
	default BlockState console(Direction front) {
		return state(Role.CONSOLE, front);
	}

	/** The merge station, panel (where the player stands) towards {@code front}. */
	default BlockState mergeStation(Direction front) {
		return state(Role.MERGE_STATION, front);
	}

	/** {@link #id()} of the active kit, {@code "vanilla"} for {@link #VANILLA}. */
	static String fingerprint() {
		HqKit k = active();
		return k == VANILLA ? "vanilla" : k.id();
	}

	/** The kit in use ({@link #VANILLA} unless the blocks module installed its own). */
	static HqKit active() {
		HqKit k = Holder.active;
		return k == null ? VANILLA : k;
	}

	/** Installs {@code kit} (null = back to vanilla). Called once at startup by the blocks module. */
	static void use(@Nullable HqKit kit) {
		Holder.active = kit;
	}

	final class Holder {
		private static volatile @Nullable HqKit active;

		private Holder() {
		}
	}
}
