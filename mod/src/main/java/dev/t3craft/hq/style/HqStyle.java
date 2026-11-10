package dev.t3craft.hq.style;

import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqFootprints;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.SignTags;
import dev.t3craft.hq.plan.Anchor;
import dev.t3craft.hq.plan.Box;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.PlanBuilder;
import dev.t3craft.hq.plan.Workstation;
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
 * {@link Anchor} and {@link Workstation}, and writes the fixture cells (desk screens,
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
		/** Cells of an occupied desk's 3×2 screen (replaced by monitors when the blocks module is on). */
		COMPUTER_ON,
		/** Vacant desk computer. */
		COMPUTER_OFF,
		/** The board's bulbs (replaced by the blocks module's board lamps when it is on); never on desks. */
		STATUS_LAMP,
		/** Stand-in for the reserved big screens and the kanban wall (until P1/P3 blocks replace it). */
		SCREEN,
		/** The hub lantern's beacon cell (replaced by the blocks module's beacon lamp). */
		BEACON,
		/** Stand-in of the archive terminal (the blocks module swaps it for {@code t3craft:archive_terminal}). */
		TERMINAL,
		/** Name plaque / sign. */
		PLAQUE
	}

	/** A sign's or plaque's text: up to four lines, the text colour (the environment's, for wing signs). */
	public record SignText(DyeColor color, String l1, String l2, String l3, String l4) {
		/** As vanilla sign block entity data (glowing text). */
		public CompoundTag signTag() {
			return SignTags.sign(color, true, l1, l2, l3, l4);
		}
	}

	protected HqStyle() {
	}

	/** Stable id used by the config ({@code hq.style}) and the preview. */
	public abstract String id();

	// ------------------------------------------------------------------ layout hook (additive)

	/**
	 * Who sits where for this style. Default: the shared spine grid ({@link HqLayout#allocate}). A
	 * style with its own layout ({@link #ownLayout()}) may encode its own places in the module
	 * indices (the campus does: parcel and building); the record and its JSON stay the same.
	 */
	public HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		return HqLayout.allocate(input, previous);
	}

	/** True when this style brings its own geometry ({@link #layoutPlan}) instead of the shared spine and wings. */
	public boolean ownLayout() {
		return false;
	}

	/**
	 * The whole plan of {@code layout} for a style with its own geometry, or null to use the shared
	 * spine geometry of {@link HqGenerator} (every style but the layout ones).
	 */
	public @Nullable BuildPlan layoutPlan(HqLayout layout) {
		return null;
	}

	/**
	 * feat/algo-map-generators: the shape and slots of {@code layout} as a {@link dev.t3craft.mapgen.MapLayout}
	 * (the generator model: where the ground is and where pieces stand), or null for a style that has no such
	 * model yet (every style but the citadel). Does not change what {@link #layoutPlan} paints.
	 */
	public dev.t3craft.mapgen.@Nullable MapLayout mapLayout(HqLayout layout) {
		return null;
	}

	/**
	 * Footprint of this layout for whoever reserves ground (site, builders, tests): the xz area every
	 * placement of the plan of {@code layout} lies in. Default: the spine grid ({@link HqFootprints}).
	 */
	public HqFootprints.Area envelope(HqLayout layout) {
		int n = 0;
		for (HqLayout.Module m : layout.modules()) {
			n = Math.max(n, m.index() + 1);
		}
		return HqFootprints.envelope(n);
	}

	/** Lowest y any block of this layout uses (relative to the HQ origin). */
	public int bottom() {
		return HqFootprints.bottom();
	}

	/** Highest y any block of this layout uses. */
	public int top() {
		return HqFootprints.top();
	}

	/** Representative block of every {@link Role} (all roles present; default orientations). */
	public abstract Map<Role, BlockState> palette();

	// ------------------------------------------------------------------ fixtures (written by the generator)

	/** Desk computer: on (occupied) or off (vacant). */
	public BlockState computer(boolean on) {
		return palette().get(on ? Role.COMPUTER_ON : Role.COMPUTER_OFF);
	}

	/** Bulb of the board (off; other modules toggle {@code lit}). Desks have no lamp: their screen shows the state. */
	public BlockState statusLamp() {
		return palette().get(Role.STATUS_LAMP);
	}

	/** Stand-in block of the reserved screen surfaces. */
	public BlockState screen() {
		return palette().get(Role.SCREEN);
	}

	/**
	 * Lamp let into a floor where the HQ would be dark. Unused by {@code HqLighting} since citadel/light-aesthetic
	 * (its fill is invisible {@code minecraft:light}); kept for styles that override it.
	 */
	BlockState floorLight() {
		return net.minecraft.world.level.block.Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
	}

	/**
	 * Lamp let into a wall or ceiling: the kit's lit shoji panel, or its vanilla stand-in. Unused by
	 * {@code HqLighting} since citadel/light-aesthetic; kept for styles that override it.
	 */
	BlockState wallLight() {
		return HqKit.active().wallPanel(true);
	}

	/** The beacon cell's block (the blocks module swaps it for its beacon lamp). */
	public BlockState beacon() {
		return palette().get(Role.BEACON);
	}

	/** The archive terminal's stand-in, screen facing {@code facing}. */
	public BlockState archiveTerminal(net.minecraft.core.Direction facing) {
		return palette().get(Role.TERMINAL).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	/** Base under a module's big screen. */
	public BlockState screenBase() {
		return palette().get(Role.BEAM);
	}

	/** A hub bench (a seat; NPCs rest here), its backrest on {@code back}. */
	public abstract BlockState bench(net.minecraft.core.Direction back);

	// ------------------------------------------------------------------ pieces

	/**
	 * The hub module (floor, walls, roof, lights, decoration); not the board, the benches, the reserved
	 * screens, the beacon cell nor the archive nook, which the generator writes after. Must keep the
	 * hologram column ({@code x = z = 0}, {@code y = 1..HOLOGRAM_TOP}) free and put a block on the
	 * beacon cell's support.
	 */
	public abstract void hub(PlanBuilder.Canvas c, boolean northDoor);

	/** The board wall at {@link HqGeometry#BOARD_Z} (frame, face, sign). The generator writes the bulbs after. */
	public abstract void board(PlanBuilder.Canvas c, SignText sign);

	/** The archive nook ({@link HqGeometry#archiveNook()}); the generator writes the terminal after. */
	public abstract void archiveNook(PlanBuilder.Canvas c);

	public abstract void portico(PlanBuilder.Canvas c);

	/**
	 * Spine row {@code r}; {@code eastSign}/{@code westSign} are the wing signs' text (null when that
	 * side has no wing).
	 */
	public abstract void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign);

	public abstract void spineCap(PlanBuilder.Canvas c, Box box);

	/** The wall across wing {@code i} at column {@code u}: the inner one (doorway) or the end cap. */
	public abstract void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner);

	/** Desk segment {@code s} of wing {@code i}: walls, floor, roof, garden strip (desk boxes are holes). */
	public abstract void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent);

	/**
	 * One desk slot. Vacant ({@code !occupied}): only the floor (vacant desks are not built). Occupied:
	 * a standing desk along the row ({@code drawer}, the cell under {@code computer}, {@code right}); the
	 * generator writes the desk's 3×2 screen after this ({@link DeskCells#screen}, the two rows above the
	 * desk top, so leave them alone) and clears {@code stand} (where the agent works, standing) and the
	 * cell above it to air.
	 */
	public abstract void desk(PlanBuilder.Canvas c, DeskCells cells);

	/**
	 * The cells of one desk, all inside its {@link HqGeometry#desk} box. {@code computer}: the screen's
	 * bottom-middle cell (over the middle desk top); {@code right}/{@code drawer}: the desk tops to the
	 * agent's right and left; {@code facing}: from the agent to the desk.
	 */
	public record DeskCells(int module, int slot, BlockPos computer, BlockPos right, BlockPos drawer, BlockPos stand,
			net.minecraft.core.Direction facing, boolean occupied) {
		/** The screen's cells ({@link Workstation#SCREEN_WIDTH} × {@link Workstation#SCREEN_HEIGHT} over the desk). */
		public java.util.List<BlockPos> screen() {
			net.minecraft.core.Direction screenFacing = facing.getOpposite();
			return Workstation.screenCells(Workstation.screenOriginFor(computer, screenFacing), screenFacing.getCounterClockWise());
		}
	}

	/**
	 * Colour of an environment (module carpet runner and sign text): one of the 16 dye colours, from a
	 * Fibonacci mix of {@link String#hashCode()} (whose value the Java spec fixes). Same for every style.
	 */
	public static DyeColor envColor(String envKey) {
		return DyeColor.byId((envKey.hashCode() * 0x9E3779B9) >>> 28);
	}

	public static DyeColor envColor(HqLayout.Module m) {
		return envColor(m.envKey().isEmpty() ? m.projectKey() : m.envKey());
	}

	@Override
	public String toString() {
		return "HqStyle[" + id() + "]";
	}
}
