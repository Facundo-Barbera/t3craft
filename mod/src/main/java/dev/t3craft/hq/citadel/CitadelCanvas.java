package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqKit;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.RingCampusStyle;
import dev.t3craft.hq.Workstation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The shared context of the citadel ({@value RingCampusGeometry#ID}): everything a {@link CitadelPiece}
 * paints with or reads. It holds the cell map being built (cells, their layer, their block-entity
 * data), the fixtures the light pass must not replace, the anchors and workstations, the layout, the
 * zone map and the barrios' lots, plus the helpers every piece shares ({@link #set}, {@link #put},
 * {@link #fixture}, {@link #fill}, {@link #anchor}, {@link #walkSpot}, {@link #zoneAt}, the column
 * keys, the {@link #lattice} of street lights, the block factories and the {@link Mats} of a barrio).
 *
 * <p>Pure and deterministic like the generator that owns it ({@code RingCampusGenerator}): a canvas is
 * created per plan, filled by the pieces in a fixed order, then turned into a {@code BuildPlan}. The
 * fields are public on purpose: a piece moved here from the generator keeps its body unchanged
 * ({@code c.cells}, {@code c.data}, {@code c.hallCols}, ...), and only prefixes the helpers with
 * {@code c.}. Statics (columns, block factories, materials) are meant to be statically imported.
 * Nothing here may change a value or the order of the calls: the plan must stay identical block by
 * block.
 *
 * <h2>Extraction map</h2>
 *
 * Every family of {@code RingCampusGenerator} goes to its own class in {@code dev.t3craft.hq.citadel},
 * one file per family, so that many agents edit without stepping on each other. A piece class is a
 * {@code final class} with a private constructor and static methods taking the canvas first
 * ({@code static void hub(CitadelCanvas c)}); the generator calls them in the same order as before.
 * Moving a family means moving its methods (and the constants only it uses) verbatim, adding the
 * {@code c.} prefix to the instance helpers, and replacing the call in the generator by a call to the
 * new class. {@code CitadelPiece} is the optional shape for a family that wants to be a value.
 *
 * <ul>
 * <li><b>{@code CitadelHub}</b> (the basilica on the podium): {@code hub}, {@code nave},
 *     {@code hubWall}, {@code board}, {@code bigScreen}, {@code core}, {@code vaultY},
 *     {@code arcadeZ}, plus {@code aisleTop()}, {@code spring()} and {@code naveHalf()} (scaled). ({@code core}, which
 *     orders the patio stairs, bridges, promenade and patio, calls into {@code CitadelGrounds}.)
 *     {@code bigScreen} is also used by the nodes: they call {@code CitadelHub.bigScreen}.</li>
 * <li><b>{@code CitadelGrounds}</b> (ring and ground): {@code ground}, {@code patioGround},
 *     {@code stepped}, {@code rampHalves}, {@code bridge}, {@code promenade}, {@code patio},
 *     {@code tree}, {@code emptyLot}. {@code tree} is also used by the barrios.</li>
 * <li><b>{@code CitadelNodes}</b> (the special rooms of the ring-1 street): {@code node},
 *     {@code nodeCell}, {@code door}, {@code tower}, {@code rotunda}, {@code cloister},
 *     {@code sunken}, {@code pillar}.</li>
 * <li><b>{@code CitadelBarrios}</b> (the lots of the projects): {@code barrio}, {@code slotGarden},
 *     {@code hall}, {@code roof}, {@code gatehouse}, {@code barrioSign}, {@code desk},
 *     {@code rotation}. ({@link Mats} and {@link #mats}, the barrio materials, stay in the canvas
 *     because several families read them.)</li>
 * <li><b>{@code CitadelFinish}</b> (groups, light pass and build): {@code groupOf}, {@code groupRank}
 *     (with the {@code GROUPS} comparator), {@code noCollision}, {@code standable}, {@code walkable}
 *     (with {@code spawn()}), {@code lightAt}, {@code replaceable}, {@code lightSpot}, {@code light},
 *     {@code build}.</li>
 * </ul>
 *
 * What stays in {@code RingCampusGenerator} is the orchestration: {@code generate} (zone map, then
 * ground, hub, core, nodes, barrios, spawn anchor, build) and {@code MIN_LIGHT}.
 *
 * <h2>Scale (citadel/scale-port)</h2>
 *
 * The whole citadel is drawn at a scale {@link #k} ({@code LayoutParams.RINGCAMPUS_SCALE}, pack key
 * {@code scale}, a decimal from 1.0 to 2.5, <b>default 1.0</b>). At {@code k = 1} every helper below
 * returns its argument and the plan is the original one block by block (same anchors, desks, spawn;
 * check with the report's signature). The geometry (radii, streets, lots, halls, nodes, spawn) lives in
 * {@link RingCampusGeometry.Dims}, here as {@link #g} (statically {@link #dims()}).
 *
 * <p>How a piece scales (new pieces must follow it; never write a bare size that should grow):
 *
 * <ul>
 * <li><b>Horizontal lengths</b> (widths, depths, radii, offsets from a centre or a wall, spacing of
 *     piers, ribs, posts, lamps): {@link #s}{@code (n)} (rounded, {@code s(-n) == -s(n)}).
 *     {@link #sd} for real thresholds ({@code r > c.sd(17.5)}), {@link #sOdd} for an <i>odd</i> width that
 *     needs a centre line (only call it on odd bases: an even base is not kept at {@code k = 1}).
 *     Static code without a canvas uses {@code dims().s(n)} / {@code dims().sd(x)} / {@code dims().sy(n)}.</li>
 * <li><b>Heights</b> of buildings and rooms: {@link #sy}{@code (n)} ({@code √k}: 1.5 gives ×1.22, so a
 *     6-high floor becomes 7, not a cavern of 9). An absolute y that sits on the podium is
 *     {@code y0() + c.sy(y - 4)}, so it stays {@code y} at scale 1. The podium {@link #y0()} and the
 *     hall floor height {@link #fh()} are already scaled (they replace the old constants {@code Y0} and
 *     {@code FH}); so are {@code CitadelFinish.spawn()}, {@code CitadelHub.aisleTop()/spring()} and the
 *     {@code g.*} radii (they replace {@code SPAWN}, {@code AISLE_TOP}, {@code SPRING},
 *     {@code RingCampusGeometry.PATIO_R/GALLERY_R/RAMP_R/STAIR_STEPS}).</li>
 * <li><b>Prefer positions from the zone</b> ({@code g.hubX()}, {@code g.patioR}, the {@code Room} of a
 *     hall, a catalog {@code Plot}'s own size) to a scaled literal: they are already at the right size.</li>
 * <li><b>Not scaled</b>: one-block steps (ramps, stairs, amphitheatre: one block or half a block a step,
 *     so the number of steps follows the height), the <i>height</i> of doors and passages (2 to 3;
 *     widen the opening instead, {@code |v - mid| <= s(1)}), wall thickness, desks and their 3×2
 *     screens, big screens and the board (fixtures: they move with their wall, they do not grow),
 *     decorative periods ({@code floorMod(..., 7)} patterns, the light lattice).</li>
 * <li>Keep {@code static final} sizes out of pieces: the scale can change at a reload, so a size that
 *     depends on it is a method (or computed inside the method that draws).</li>
 * </ul>
 */
public final class CitadelCanvas {
	/** Layer of a layout cell: placed with the solid blocks. */
	public static final int SOLID = 0;
	/** Layer of a layout cell: a decoration (signs, plaques), placed after the solid blocks. */
	public static final int DECOR = 1;
	/** Layer of a layout cell: a hanging block, placed top-down. */
	public static final int HANGING = 2;
	public static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** The podium's height: the hub and the core stand on it (4 at scale 1, {@code sy(4)}; was {@code Y0}). */
	public static int y0() {
		return RingCampusGeometry.podium();
	}

	/** Height of a hall's floor (6 at scale 1, {@code sy(6)}; was {@code FH}). */
	public static int fh() {
		return RingCampusGeometry.floorH();
	}

	/** The citadel's dimensions at the scale in use (for static code without a canvas). */
	public static RingCampusGeometry.Dims dims() {
		return RingCampusGeometry.dims();
	}

	// ------------------------------------------------------------------ materials (cherry palette)

	public static final BlockState FOOT = b(Blocks.DEEPSLATE_TILES);
	public static final BlockState BAMBOO = b(Blocks.BAMBOO_MOSAIC);
	public static final BlockState CALCITE = b(Blocks.CALCITE);
	public static final BlockState LOG = b(Blocks.CHERRY_LOG);
	public static final BlockState PLANKS = b(Blocks.CHERRY_PLANKS);
	public static final BlockState GLASS = b(Blocks.GLASS);
	public static final BlockState GRASS = b(Blocks.GRASS_BLOCK);
	public static final BlockState MOSS = b(Blocks.MOSS_BLOCK);
	public static final BlockState WATER = b(Blocks.WATER);
	public static final BlockState RIB = b(Blocks.POLISHED_BLACKSTONE_BRICKS);
	/**
	 * A froglight for <b>design</b> lamps only (a lit band on a tower, a cupola, the clock marks of the rotunda,
	 * a promenade post). Never let it into a floor, wall or ceiling to cover a dark spot: that is the job of the
	 * invisible fill ({@link #invisibleLight}, {@code CitadelFinish#light}). See docs/feat-hq-lighting.md.
	 */
	public static final BlockState LIGHT = b(Blocks.PEARLESCENT_FROGLIGHT);

	// ------------------------------------------------------------------ state

	public final RingCampusStyle style;
	public final HqKit kit;
	/** The citadel's dimensions at this plan's scale (read once, when the canvas is made). */
	public final RingCampusGeometry.Dims g;
	/** The scale of this plan ({@code LayoutParams.RINGCAMPUS_SCALE}, 1.0 = the original citadel). */
	public final double k;

	public final Map<Long, BlockState> cells = new HashMap<>();
	public final Map<Long, CompoundTag> data = new HashMap<>();
	public final Map<Long, Integer> layers = new HashMap<>();
	/** Fixture cells the light pass never replaces (desks, screens, terminal, board). */
	public final Set<Long> fixtures = new HashSet<>();
	public final TreeMap<String, Anchor> anchors = new TreeMap<>();
	public final List<Workstation> workstations = new ArrayList<>();
	private int walk;
	private int bench;

	/** Rings of lots (set by the generator before any piece runs). */
	public int rings;
	public HqLayout layout;
	/** lot → module slot b (0..5) → module. */
	public final Map<Integer, TreeMap<Integer, HqLayout.Module>> lots = new TreeMap<>();
	public Map<Long, Zone> zones = Map.of();
	public final Map<Integer, List<Long>> lotCols = new TreeMap<>();
	/** Ground columns of the built halls: {lot, hall}. */
	public final Map<Long, int[]> hallCols = new HashMap<>();

	public CitadelCanvas(RingCampusStyle style, HqKit kit) {
		this.style = style;
		this.kit = kit;
		this.g = RingCampusGeometry.dims();
		this.k = g.k;
	}

	// ------------------------------------------------------------------ scale (see the class comment)

	/** A horizontal length (width, depth, radius, offset, spacing) at this plan's scale, rounded; {@code s(-n) == -s(n)}. */
	public int s(int base) {
		return RingCampusGeometry.s(k, base);
	}

	/** A real horizontal length (radius, threshold) at this plan's scale. */
	public double sd(double base) {
		return RingCampusGeometry.sd(k, base);
	}

	/** A height at this plan's scale: {@code base · √k}, rounded, so floors and ceilings stay playable. */
	public int sy(int base) {
		return RingCampusGeometry.sy(k, base);
	}

	/** An odd width (a room or passage with a centre line) at this plan's scale; call it on odd bases only. */
	public int sOdd(int base) {
		return RingCampusGeometry.sOdd(k, base);
	}

	/** An absolute height on the podium: {@code y} at scale 1, {@code y0() + sy(y - 4)} otherwise. */
	public int py(int y) {
		return y0() + sy(y - 4);
	}

	// ------------------------------------------------------------------ cell helpers

	public static long col(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	public static int colX(long c) {
		return (int) (c >> 32);
	}

	public static int colZ(long c) {
		return (int) c;
	}

	public void set(int x, int y, int z, BlockState s) {
		put(x, y, z, s, SOLID);
	}

	public void set(BlockPos p, BlockState s) {
		put(p.getX(), p.getY(), p.getZ(), s, SOLID);
	}

	public void put(int x, int y, int z, BlockState s, int layer) {
		long k = BlockPos.asLong(x, y, z);
		cells.put(k, s);
		layers.put(k, layer);
		data.remove(k);
	}

	public void fixture(BlockPos p, BlockState s) {
		set(p, s);
		fixtures.add(p.asLong());
	}

	public void fill(int x, int y1, int y2, int z, BlockState s) {
		for (int y = y1; y <= y2; y++) {
			set(x, y, z, s);
		}
	}

	public @Nullable BlockState at(int x, int y, int z) {
		return cells.get(BlockPos.asLong(x, y, z));
	}

	public Zone zoneAt(int x, int z) {
		Zone zn = zones.get(col(x, z));
		return zn == null ? Zone.NONE : zn;
	}

	public void anchor(Anchor a) {
		if (anchors.put(a.name(), a) != null) {
			throw new IllegalStateException("duplicate anchor " + a.name());
		}
	}

	public void walkSpot(BlockPos p, Direction f) {
		anchor(Anchor.at(AnchorNames.walkHub(walk++), p, f));
	}

	/** The index of the next hub bench anchor (benches are numbered in the order they are placed). */
	public int nextBench() {
		return bench++;
	}

	/**
	 * A lattice of positions every 41 cells that leaves no open floor cell more than 4 steps from one. Since
	 * citadel/light-aesthetic it only seeds the hub's invisible fill: no visible light is put on it.
	 */
	public static boolean lattice(int x, int z) {
		return Math.floorMod(4 * x + 5 * z, dev.t3craft.hq.pack.LayoutParams.RINGCAMPUS_STREET_LIGHT.get()) == 0;
	}

	// ------------------------------------------------------------------ block factories

	public static BlockState b(Block b) {
		return b.defaultBlockState();
	}

	/**
	 * The invisible vanilla light block ({@code minecraft:light}) of {@code level} (1..15): no collision,
	 * replaceable, drawn by nobody. It is what lights the floor of the citadel (see
	 * {@code CitadelFinish#light}), at moderate levels; the light one sees comes from design lamps.
	 */
	public static BlockState invisibleLight(int level) {
		return Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, level);
	}

	public BlockState floor() {
		return kit.floor();
	}

	public BlockState accent() {
		return kit.floorAccent();
	}

	public static BlockState slab(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	public static BlockState stairs(Block b, Direction facing) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	public static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
	}

	public static BlockState terracotta(DyeColor c) {
		return Blocks.DYED_TERRACOTTA.pick(c).defaultBlockState();
	}

	/** A barrio's wall and roof materials (each barrio picks one of three by its lot id). */
	public record Mats(BlockState wall, BlockState post, BlockState band, Block roof, BlockState ridge, BlockState ceiling) {
	}

	public Mats mats(int lot, DyeColor env) {
		return switch (Math.floorMod(lot * 5 + RingCampusGeometry.ringOfLot(lot), 3)) {
			case 0 -> new Mats(kit.wallPanel(false), kit.beam(Direction.Axis.Y), kit.beam(Direction.Axis.Y), Blocks.CHERRY_STAIRS, PLANKS, PLANKS);
			case 1 -> new Mats(terracotta(env), LOG, CALCITE, Blocks.DEEPSLATE_TILE_STAIRS, FOOT, PLANKS);
			default -> new Mats(CALCITE, b(Blocks.CHERRY_WOOD), PLANKS, Blocks.BRICK_STAIRS, b(Blocks.BRICKS), b(Blocks.BAMBOO_PLANKS));
		};
	}
}
