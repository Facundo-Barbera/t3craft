package dev.t3craft.hq;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The look of the HQ: a palette (blocks by {@link Role}) and the interchangeable pieces that fill
 * each module's cells (hub shell, board frame, portico, spine, wing walls and roofs, desk furniture,
 * gardens, lights). A style only decides <em>which block goes in each cell</em> of a module:
 * {@link HqGenerator} owns the geometry (the same {@link HqGeometry} boxes for every style), every
 * {@link Anchor} and {@link Workstation}, and writes the fixture cells (computers, status lamps,
 * board bulbs, archived computers) itself after the piece, so NPCs, {@link HqFixtures}, site
 * envelopes and builders see the same plan shape whatever the style.
 *
 * <p>Pieces get a {@link PlanBuilder.Canvas} that only lets them write the module's own cells (and
 * never above {@link HqGeometry#TOP}). Styles must be pure and deterministic (no randomness, no
 * clock, no world access), like the generator.
 *
 * <p>Styles are listed in {@link HqStyles}; the active one comes from {@code hq.style} in
 * {@code config/t3craft.json} (default {@link HqStyles#DEFAULT_ID}).
 */
public abstract class HqStyle {
	/** What a block is for. Every style maps every role (see {@link #palette()}). */
	public enum Role {
		/** Interior floor of the halls (wing, spine, hub centre). */
		FLOOR,
		/** Plinth / footing under walls and the floor border. */
		FOOTING,
		/** Opaque wall infill. */
		WALL,
		/** Vertical structure (posts, pillars). */
		COLUMN,
		/** Horizontal structure (beams, lintels). */
		BEAM,
		/** Main roof surface (a stairs block, oriented by the piece). */
		ROOF,
		/** Flat roof parts and roof trim (a slab). */
		ROOF_TRIM,
		/** Ridge / roof cap. */
		RIDGE,
		/** Glazing (panes or panels). */
		WINDOW,
		/** Doorway frame or door detail. */
		DOOR,
		/** Furniture accents (shelves, pots). */
		DECOR,
		/** Outdoor path. */
		PATH,
		/** Garden ground. */
		GARDEN,
		/** Garden tree / bush canopy. */
		GARDEN_TREE,
		/** Light source. */
		LIGHT,
		/** Desk top. */
		DESK,
		/** Seat (a stairs block, oriented by the piece). */
		CHAIR,
		/** The attention board's face. */
		BOARD,
		/** Occupied desk computer (replaced by a monitor when the blocks module is on). */
		COMPUTER_ON,
		/** Vacant desk computer. */
		COMPUTER_OFF,
		/** Status lamp (replaced by the blocks module's lamp when it is on). */
		STATUS_LAMP
	}

	HqStyle() {
	}

	/** Stable id used by the config ({@code hq.style}) and the preview. */
	public abstract String id();

	/** Representative block of every {@link Role} (all roles present; default orientations). */
	public abstract Map<Role, BlockState> palette();

	// ------------------------------------------------------------------ fixtures (written by the generator)

	/** Desk computer: on (occupied) or off (vacant). */
	BlockState computer(boolean on) {
		return palette().get(on ? Role.COMPUTER_ON : Role.COMPUTER_OFF);
	}

	/** Status lamp of a desk and bulb of the board (off; other modules toggle {@code lit}). */
	BlockState statusLamp() {
		return palette().get(Role.STATUS_LAMP);
	}

	// ------------------------------------------------------------------ pieces

	/** The hub module (floor, walls, roof, benches, lights, the stairwell top); not the board. */
	abstract void hub(PlanBuilder.Canvas c, boolean northDoor);

	/** The board wall at {@link HqGeometry#BOARD_Z} (frame, face, sign). The generator writes the bulbs after. */
	abstract void board(PlanBuilder.Canvas c, CompoundTag sign);

	/** The archive basement (shell, stairs down, shelves); the generator writes archived computers after. */
	abstract void archive(PlanBuilder.Canvas c);

	abstract void portico(PlanBuilder.Canvas c);

	/**
	 * Spine row {@code r}; {@code eastSign}/{@code westSign} are the wing signs' block entity data
	 * (null when that side has no wing).
	 */
	abstract void spineRow(PlanBuilder.Canvas c, int r, @Nullable CompoundTag eastSign, @Nullable CompoundTag westSign);

	abstract void spineCap(PlanBuilder.Canvas c, Box box);

	/** The wall across wing {@code i} at column {@code u}: the inner one (doorway) or the end cap. */
	abstract void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner);

	/** Desk segment {@code s} of wing {@code i}: walls, floor, roof, garden strip (desk boxes are holes). */
	abstract void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent);

	/**
	 * Desk furniture of one slot. {@code computer} and {@code lamp} are written by the generator
	 * after this; {@code seat} must hold a seat.
	 */
	abstract void desk(PlanBuilder.Canvas c, DeskCells cells);

	/** The cells of one desk, all inside its {@link HqGeometry#desk} box. */
	record DeskCells(int module, int slot, BlockPos computer, BlockPos lamp, BlockPos drawer, BlockPos seat,
			net.minecraft.core.Direction facing, boolean occupied) {
	}

	/**
	 * Colour of an environment (module carpet runner and sign text): one of the 16 dye colours, from a
	 * Fibonacci mix of {@link String#hashCode()} (whose value the Java spec fixes). Same for every style.
	 */
	static DyeColor envColor(String envKey) {
		return DyeColor.byId((envKey.hashCode() * 0x9E3779B9) >>> 28);
	}

	static DyeColor envColor(HqLayout.Module m) {
		return envColor(m.envKey().isEmpty() ? m.projectKey() : m.envKey());
	}

	@Override
	public String toString() {
		return "HqStyle[" + id() + "]";
	}
}
