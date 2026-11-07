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
 * board bulbs, beacon, archive terminal, reserved screens) itself after the piece, so NPCs, {@link HqFixtures}, site
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
		/** Hub bench seat (a stairs block, oriented by the piece); desks have no chairs, agents stand. */
		BENCH,
		/** The attention board's face. */
		BOARD,
		/** Occupied desk computer (replaced by a monitor when the blocks module is on). */
		COMPUTER_ON,
		/** Vacant desk computer. */
		COMPUTER_OFF,
		/** Status lamp (replaced by the blocks module's lamp when it is on). */
		STATUS_LAMP,
		/** Stand-in for the reserved big screens and the kanban wall (until P1/P3 blocks replace it). */
		SCREEN,
		/** The hub lantern's beacon cell (replaced by the blocks module's beacon lamp). */
		BEACON,
		/** Base under the archive terminal's screen. */
		TERMINAL,
		/** Name plaque / sign. */
		PLAQUE
	}

	/**
	 * Optional replacement of a style's role blocks by another module's own blocks (feat/hq-blocks-kit:
	 * standing desk, wall and shoji panels, floor tiles, lantern, plaque, beam…), installed once at
	 * startup like {@link HqFixtures}. Without a kit every style is pure vanilla.
	 */
	@FunctionalInterface
	public interface Kit {
		/**
		 * The block to use for {@code role} in style {@code styleId} instead of {@code vanilla}, or null to
		 * keep it. Must be pure; the returned state should accept the same orientation properties as
		 * {@code vanilla} where the piece orients it (stairs facing, log axis), others are ignored.
		 */
		@Nullable BlockState replace(String styleId, Role role, BlockState vanilla);

		/** Short stable name (goes into build hashes, like {@link HqFixtures#id()}). */
		default String id() {
			return "kit";
		}
	}

	/** The installed kit, or null (vanilla). */
	public static @Nullable Kit kit() {
		return KitHolder.kit;
	}

	/** Installs a kit (null = vanilla). Called once by the blocks module. */
	public static void useKit(@Nullable Kit kit) {
		KitHolder.kit = kit;
	}

	private static final class KitHolder {
		private static volatile @Nullable Kit kit;
	}

	/**
	 * {@code vanilla}, or the kit's block for {@code role} with {@code vanilla}'s properties copied
	 * where the kit block has them.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	protected final BlockState kitted(Role role, BlockState vanilla) {
		Kit k = kit();
		if (k == null) {
			return vanilla;
		}
		BlockState r = k.replace(id(), role, vanilla);
		if (r == null) {
			return vanilla;
		}
		for (net.minecraft.world.level.block.state.properties.Property p : vanilla.getProperties()) {
			if (r.hasProperty(p)) {
				r = r.setValue(p, vanilla.getValue(p));
			}
		}
		return r;
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

	/** Stand-in block of the reserved screen surfaces. */
	BlockState screen() {
		return palette().get(Role.SCREEN);
	}

	/** The beacon cell's block (the blocks module swaps it for its beacon lamp). */
	BlockState beacon() {
		return palette().get(Role.BEACON);
	}

	/** Base block under the archive terminal's screen. */
	BlockState terminalBase() {
		return palette().get(Role.TERMINAL);
	}

	/** A hub bench (a seat; NPCs rest here), its backrest on {@code back}. */
	abstract BlockState bench(net.minecraft.core.Direction back);

	// ------------------------------------------------------------------ pieces

	/**
	 * The hub module (floor, walls, roof, lights, decoration); not the board, the benches, the reserved
	 * screens, the beacon cell nor the archive nook, which the generator writes after. Must keep the
	 * hologram column ({@code x = z = 0}, {@code y = 1..HOLOGRAM_TOP}) free and put a block on the
	 * beacon cell's support.
	 */
	abstract void hub(PlanBuilder.Canvas c, boolean northDoor);

	/** The board wall at {@link HqGeometry#BOARD_Z} (frame, face, sign). The generator writes the bulbs after. */
	abstract void board(PlanBuilder.Canvas c, CompoundTag sign);

	/** The archive nook ({@link HqGeometry#archiveNook()}); the generator writes the terminal after. */
	abstract void archiveNook(PlanBuilder.Canvas c);

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
	 * One desk slot. Vacant ({@code !occupied}): only the floor (vacant desks are not built). Occupied:
	 * a standing desk; {@code computer} and {@code lamp} are written by the generator after this, and
	 * {@code stand} (where the agent works, standing) and the cell above it are cleared to air.
	 */
	abstract void desk(PlanBuilder.Canvas c, DeskCells cells);

	/** The cells of one desk, all inside its {@link HqGeometry#desk} box. {@code facing}: from the agent to the desk. */
	record DeskCells(int module, int slot, BlockPos computer, BlockPos lamp, BlockPos drawer, BlockPos stand,
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
