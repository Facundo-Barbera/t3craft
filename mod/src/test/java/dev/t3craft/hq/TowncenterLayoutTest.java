package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.layout.HqLayouts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * design/hybrid-towncenter ("Centro urbano con torres"): ensanche manzanas round a civic plaza,
 * corner towers for busy projects, skybridges between facing towers. Checks the registry, the growth
 * rules (podium, then up the tower, then the own manzana, then next door; new projects refill the
 * lowest free parcel), determinism, stability from 4 to 10 to 20 projects, valid and reachable anchors
 * (tower floors and skybridges included), footprints, 3×2 screens without lamps, no free-standing
 * lanterns, supported decoration and block light >= 10 on every cell an NPC can reach.
 */
class TowncenterLayoutTest {
	private static HqStyle style;

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
		style = HqStyles.byId(TowncenterGrid.ID);
	}

	/** {@code n} projects with varied thread counts (the render's roster), one settled thread each. */
	static HqInput roster(int n) {
		return EnsancheLayoutTest.roster(n);
	}

	private static HqGenerator.Result plan(HqInput in, HqLayout prev) {
		return HqGenerator.plan(in, prev, style);
	}

	/** 4 → 10 → 20 projects, each step from the previous layout (like the live HQ). */
	private static List<HqGenerator.Result> growth() {
		List<HqGenerator.Result> out = new ArrayList<>();
		HqLayout prev = null;
		for (int n : new int[] {4, 10, 20}) {
			HqGenerator.Result r = plan(roster(n), prev);
			out.add(r);
			prev = r.layout();
		}
		return out;
	}

	private static int cellOf(int module) {
		return EnsancheGrid.cellOfParcel(TowncenterGrid.parcelOfModule(module));
	}

	// ------------------------------------------------------------------ registry

	@Test
	void layoutIsRegisteredSelectableAndCherry() {
		assertNotNull(style);
		assertTrue(style.ownLayout());
		assertEquals(TowncenterGrid.ID, HqLayouts.resolve("hybrid-towncenter").id());
		assertSame(style, HqStyles.forLayout("hybrid-towncenter"));
		assertFalse(HqStyles.all().contains(style), "the shared-geometry list is unchanged");
		assertFalse(HqStyles.towns().contains(style), "the ensanche's town list is unchanged");
		assertEquals(new CherryStyle().palette(), style.palette());
		assertEquals(TowncenterGrid.TOP, style.top());
	}

	// ------------------------------------------------------------------ growth rules

	@Test
	void aProjectFillsItsPodiumThenGrowsUpThenTakesItsManzanaThenNextDoor() {
		HqInput in = HqInput.builder().project("e:a", "A", "e", "E").build();
		HqLayout l = null;
		List<Integer> order = new ArrayList<>();
		int rooms = TowncenterGrid.FLOORS * 4 + 2;
		for (int t = 0; t < HqLayout.DESKS * rooms; t++) {
			in = HqTestSupport.plus(in, "e:a", "e:a-" + String.format(Locale.ROOT, "%03d", t));
			l = HqLayout.allocate(in, l, style);
			for (HqLayout.Module m : l.modules()) {
				if (!order.contains(m.index())) {
					order.add(m.index());
				}
			}
		}
		assertEquals(rooms, order.size());
		int p0 = TowncenterGrid.parcelOfModule(order.get(0));
		assertEquals(1, EnsancheGrid.cellOfParcel(p0), "the first project takes the first work manzana of the spiral");
		for (int i = 0; i < TowncenterGrid.FLOORS; i++) {
			assertEquals(TowncenterGrid.module(p0, i), order.get(i), "room " + i + ": podium, then the tower floor by floor");
		}
		// then the other parcels of its own manzana, each podium first and up its tower
		for (int i = TowncenterGrid.FLOORS; i < TowncenterGrid.FLOORS * 4; i++) {
			assertEquals(1, cellOf(order.get(i)), "room " + i + " stays in its manzana");
			assertEquals(i % TowncenterGrid.FLOORS, TowncenterGrid.floorOfModule(order.get(i)), "room " + i);
		}
		int next = cellOf(order.get(TowncenterGrid.FLOORS * 4));
		assertTrue(EnsancheGrid.neighbours(1).contains(next), "only then a manzana next door, got cell " + next);
	}

	@Test
	void newProjectsRefillTheLowestFreeParcel() {
		HqLayout l = HqLayout.allocate(HqTestSupport.input(4, 1), null, style);
		Set<Integer> cells = new TreeSet<>();
		for (HqLayout.Module m : l.modules()) {
			cells.add(cellOf(m.index()));
			assertEquals(0, TowncenterGrid.floorOfModule(m.index()), "a one-room project lives in a podium");
		}
		assertEquals(Set.of(1), cells, "four one-room projects densify the first manzana");
		HqLayout five = HqLayout.allocate(HqTestSupport.input(5, 1), l, style);
		assertEquals(4, cellOf(five.modulesOf("env:p4").get(0).index()), "the fifth opens the next work manzana");
		// free a parcel: the next new project takes that hole, nothing else moves
		int freed = l.modulesOf("env:p1").get(0).index();
		HqInput without = HqTestSupport.minus(HqTestSupport.input(5, 1), "env:p1-t0");
		HqLayout l2 = HqLayout.allocate(without, five, style);
		for (HqLayout.Module m : l2.modules()) {
			assertEquals(five.moduleAt(m.index()).projectKey(), m.projectKey(), "nobody moves");
		}
		HqLayout l3 = HqLayout.allocate(HqTestSupport.plus(without, "env:zz", "env:zz-t0"), l2, style);
		assertEquals(freed, l3.modulesOf("env:zz").get(0).index(), "the hole is refilled first");
	}

	/** The render's roster ({@code TownRender.roster}): the gallery shows exactly this HQ. */
	static HqInput renderRoster(int n) {
		int[] threads = {9, 4, 7, 2, 13, 3, 1, 6, 5, 20, 2, 8, 4, 3, 11, 1, 6, 2, 5, 3};
		String[] names = {"t3craft", "bridge", "website", "infra", "mobile", "docs", "api", "billing", "search", "platform", "auth", "analytics", "design",
				"cli", "data", "ops", "chat", "payments", "gateway", "scheduler"};
		String[] envs = {"vps", "vps", "acmesite", "laptop", "cloud", "vps", "acmesite", "cloud", "laptop", "vps", "cloud", "acmesite", "laptop", "vps",
				"cloud", "acmesite", "vps", "cloud", "laptop", "acmesite"};
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String name = String.format(Locale.ROOT, "p%02d-%s", p, names[p]);
			String key = envs[p] + ":" + name;
			for (int t = 0; t < threads[p]; t++) {
				b.thread(key, names[p], envs[p], envs[p].toUpperCase(Locale.ROOT), key + "-t" + t);
			}
			b.thread(key, names[p], envs[p], envs[p].toUpperCase(Locale.ROOT), key + "-s0", true);
		}
		return b.build();
	}

	@Test
	void twentyProjectsMakeASkylineWithASkybridge() {
		HqGenerator.Result r = null;
		for (int n : new int[] {4, 10, 20}) {
			r = plan(renderRoster(n), r == null ? null : r.layout());
		}
		int towers = 0;
		Set<Integer> parcels = new HashSet<>();
		for (HqLayout.Module m : r.layout().modules()) {
			if (TowncenterGrid.floorOfModule(m.index()) >= 1 && parcels.add(TowncenterGrid.parcelOfModule(m.index()))) {
				towers++;
			}
		}
		assertTrue(towers >= 4, "busy projects grow towers: " + towers);
		assertTrue(r.plan().groups().stream().anyMatch(g -> g.startsWith("bridge_")), "facing towers are joined: " + r.plan().groups());
		assertTrue(r.plan().footprint().maxY() > 30, "the towers and the clock tower rise over the podiums");
	}

	// ------------------------------------------------------------------ determinism and stability

	@Test
	void generationIsDeterministicIdempotentAndIgnoresRosterOrder() {
		HqInput in = roster(10);
		HqGenerator.Result a = plan(in, null);
		HqGenerator.Result b = plan(in, null);
		assertEquals(a.plan().fingerprint(), b.plan().fingerprint());
		assertEquals(a.layout(), b.layout());
		HqGenerator.Result again = plan(in, a.layout());
		assertEquals(a.layout(), again.layout(), "allocate(in, allocate(in)) == allocate(in)");
		assertEquals(a.plan().fingerprint(), again.plan().fingerprint());
		List<HqInput.Entry> roster = HqTestSupport.roster(in);
		Collections.shuffle(roster, new Random(11));
		assertEquals(a.plan().fingerprint(), plan(HqInput.fromRoster(roster), null).plan().fingerprint());
		assertEquals(a.layout(), HqLayout.fromJson(a.layout().toJson()), "the layout survives its JSON");
	}

	@Test
	void growingFrom4To10To20ProjectsNeverMovesABuilding() {
		List<HqGenerator.Result> g = growth();
		for (int i = 1; i < g.size(); i++) {
			HqGenerator.Result before = g.get(i - 1);
			HqGenerator.Result after = g.get(i);
			for (HqLayout.Module m : before.layout().modules()) {
				assertEquals(m, after.layout().moduleAt(m.index()), "room " + m.index() + " kept with the same desks");
			}
			Map<String, Workstation> ws = new HashMap<>();
			for (Workstation w : after.plan().workstations()) {
				ws.put(w.threadId(), w);
			}
			for (Workstation w : before.plan().workstations()) {
				assertEquals(w, ws.get(w.threadId()), "desk of " + w.threadId() + " unchanged");
			}
			for (String group : before.plan().groups()) {
				boolean parcel = group.startsWith("parcel_");
				if (!parcel && !group.startsWith("patio_") && !group.startsWith("bridge_") && !group.equals("clock") && !group.equals("signal")
						&& !group.equals(HqGenerator.GROUP_ARCHIVE)) {
					continue;
				}
				if (parcel && changed(before.layout(), after.layout(), Integer.parseInt(group.substring(7)))) {
					continue;
				}
				List<Placement> a = before.plan().placements(group);
				List<Placement> b = after.plan().placements(group);
				assertEquals(a.size(), b.size(), group);
				for (int j = 0; j < a.size(); j++) {
					assertEquals(a.get(j).rel(), b.get(j).rel(), group);
					assertSame(a.get(j).state().getBlock(), b.get(j).state().getBlock(), group + " at " + a.get(j).rel());
				}
			}
		}
		var fp = g.get(2).plan().footprint();
		assertTrue(fp.sizeX() >= 2 * EnsancheGrid.PITCH && fp.sizeZ() >= 2 * EnsancheGrid.PITCH, "a barrio, not an avenue: " + fp);
	}

	private static boolean changed(HqLayout a, HqLayout b, int parcel) {
		for (int f = 0; f < TowncenterGrid.FLOORS; f++) {
			int i = TowncenterGrid.module(parcel, f);
			HqLayout.Module x = a.moduleAt(i);
			HqLayout.Module y = b.moduleAt(i);
			if (x == null ? y != null : !x.equals(y)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ anchors and walking

	@ParameterizedTest(name = "{0} projects")
	@ValueSource(ints = {4, 10, 20})
	void anchorsAreFreeStandableAndReachableFromTheSpawn(int n) {
		HqGenerator.Result r = result(n);
		HqAnchorPlacementTest.assertAnchorsValid(r.plan(), n + " projects");
		for (HqLayout.Module mod : r.layout().modules()) {
			assertNotNull(r.plan().anchor(AnchorNames.module(mod.index())), "door anchor of room " + mod.index());
		}
		for (String a : List.of("walk_clock", "walk_signal", "walk_lounge_0", "walk_plazuela_2", AnchorNames.ARCHIVE, AnchorNames.HOLOGRAM,
				AnchorNames.BEACON, AnchorNames.THREAD_WALL, AnchorNames.ARCHIVE_TERMINAL, AnchorNames.BOARD, AnchorNames.BOARD_FRONT, AnchorNames.HUB)) {
			assertNotNull(r.plan().anchor(a), a);
		}
	}

	private static HqGenerator.Result result(int n) {
		for (HqGenerator.Result r : growth()) {
			if (r.layout().modules().stream().map(HqLayout.Module::projectKey).distinct().count() == n) {
				return r;
			}
		}
		throw new IllegalStateException("no step with " + n + " projects");
	}

	@Test
	void everyTowerFloorIsReachableByTheAtriumStair() {
		// one project with 36 threads: the podium room and five tower floors
		BuildPlan p = plan(HqTestSupport.input(1, HqLayout.DESKS * TowncenterGrid.FLOORS), null).plan();
		Set<Integer> floors = new TreeSet<>();
		for (Workstation w : p.workstations()) {
			floors.add(w.deskAnchor().block().getY() / EnsancheGrid.STOREY);
		}
		assertEquals(Set.of(0, 1, 2, 3, 4, 5), floors);
		HqAnchorPlacementTest.assertAnchorsValid(p, "six floors");
	}

	@Test
	void facingTowersAreJoinedByAWalkableSkybridge() {
		// two projects with towers on facing corners: cell 1 (0,-1) side 1 and its west neighbour
		int p = EnsancheGrid.parcel(1, 1);
		int q = TowncenterGrid.streetPartner(p);
		assertEquals(EnsancheGrid.parcel(EnsancheGrid.index(-1, -1), 2), q, "the north-west corner faces the west neighbour's north-east corner");
		assertEquals(p, TowncenterGrid.endPartner(q), "and back");
		int y = EnsancheGrid.STOREY * TowncenterGrid.BRIDGE_FLOOR;
		BlockPos across = TowncenterGrid.frameOf(p).pos(TowncenterGrid.BRIDGE_DOOR, y + 1, -EnsancheGrid.STREET - 1);
		Box door = TowncenterGrid.endDoor(q);
		assertTrue(door.contains(across.getX(), across.getY(), across.getZ()), "the doors line up across the street: " + across + " " + door);
		List<HqLayout.Module> mods = new ArrayList<>();
		mods.add(module(TowncenterGrid.module(p, 0), "e:a", 0));
		mods.add(module(TowncenterGrid.module(p, 1), "e:a", 6));
		mods.add(module(TowncenterGrid.module(q, 0), "e:b", 0));
		mods.add(module(TowncenterGrid.module(q, 2), "e:b", 6));
		mods.sort((x, z) -> Integer.compare(x.index(), z.index()));
		HqLayout layout = new HqLayout(mods, List.of(), List.of());
		BuildPlan plan = new HqGenerator(style).generate(layout);
		assertTrue(plan.groups().contains("bridge_" + p + "_" + q), plan.groups().toString());
		assertFalse(plan.groups().contains("plug_" + p + "_d"));
		assertFalse(plan.groups().contains("plug_" + q + "_a"));
		assertTrue(plan.groups().contains("plug_" + p + "_a"), "the other door is a window");
		HqAnchorPlacementTest.assertAnchorsValid(plan, "skybridge");
		Set<BlockPos> reach = new PlanWalkability(plan).reachable(plan.anchor(AnchorNames.SPAWN).block());
		BlockPos mid = TowncenterGrid.frameOf(p).pos(TowncenterGrid.BRIDGE_DOOR, y + 1, -EnsancheGrid.STREET / 2);
		assertTrue(reach.contains(mid), "the middle of the skybridge is reachable on foot: " + mid);
		assertLit(plan, reach, "skybridge");
	}

	private static HqLayout.Module module(int index, String project, int first) {
		List<HqLayout.Desk> desks = new ArrayList<>();
		for (int s = 0; s < HqLayout.DESKS; s++) {
			desks.add(new HqLayout.Desk(s, project + "-t" + (first + s)));
		}
		return new HqLayout.Module(index, project, project, "e", "E", desks);
	}

	// ------------------------------------------------------------------ footprints, screens, decoration

	@Test
	void everythingStaysInsideItsFootprints() {
		for (HqGenerator.Result r : growth()) {
			BuildPlan p = r.plan();
			HqFootprints.Area env = TowncenterGrid.envelope(TowncenterGrid.maxCell(r.layout()));
			assertEquals(env, style.envelope(r.layout()));
			for (Placement pl : p.placements()) {
				BlockPos c = pl.rel();
				assertTrue(env.contains(c.getX(), c.getZ()), "outside the envelope: " + c);
				assertTrue(c.getY() >= 0 && c.getY() <= TowncenterGrid.TOP, "height " + c);
			}
			for (BuildPlan.Module m : p.modules()) {
				Box b = m.box();
				String g = m.group();
				HqFootprints.Area area;
				if (g.startsWith("parcel_") || g.startsWith("plug_")) {
					area = EnsancheGrid.parcelArea(Integer.parseInt(g.split("_")[1]));
				} else if (g.startsWith("bridge_")) {
					String[] s = g.split("_");
					area = EnsancheGrid.cellArea(EnsancheGrid.cellOfParcel(Integer.parseInt(s[1])))
							.union(EnsancheGrid.cellArea(EnsancheGrid.cellOfParcel(Integer.parseInt(s[2]))));
				} else if (g.startsWith("patio_")) {
					area = EnsancheGrid.patioArea(Integer.parseInt(g.substring(6)));
				} else if (g.startsWith("streets_") || g.startsWith("solar_") || g.startsWith("plazuela_")) {
					area = EnsancheGrid.cellArea(Integer.parseInt(g.substring(g.indexOf('_') + 1)));
				} else if (g.equals("lounge")) {
					area = EnsancheGrid.cellArea(3);
				} else {
					area = EnsancheGrid.blockArea(0);
				}
				assertTrue(area.contains(b.minX(), b.minZ()) && area.contains(b.maxX(), b.maxZ()), m.id() + " " + b + " outside " + area);
			}
		}
	}

	@Test
	void desksHaveOne3x2ScreenNoLampsAndThereAreNoLanterns() {
		BuildPlan p = growth().get(2).plan();
		Set<BlockPos> boardLamps = new HashSet<>();
		for (Anchor a : p.anchors().values()) {
			if (a.name().startsWith(AnchorNames.BOARD_LAMP)) {
				boardLamps.add(a.block());
			}
		}
		assertEquals(4, boardLamps.size());
		Set<BlockPos> screens = new HashSet<>();
		for (Workstation w : p.workstations()) {
			List<BlockPos> cells = w.screenCells();
			assertEquals(Workstation.SCREEN_WIDTH * Workstation.SCREEN_HEIGHT, cells.size());
			for (BlockPos c : cells) {
				assertEquals(style.computer(true), p.at(c).state(), "screen cell " + c);
				assertTrue(screens.add(c), "screens never shared");
				assertNotEquals(c, w.deskAnchor().block());
				assertNotEquals(c, w.deskAnchor().block().above());
			}
			assertEquals(w.deskAnchor().block().relative(w.deskAnchor().facing()).above(), w.computerPos(), "screen right in front, eye level");
			assertNull(p.anchor(AnchorNames.lamp(w.threadId())), "no desk lamp anchor");
		}
		for (Placement pl : p.placements()) {
			BlockState s = pl.state();
			if (s.getBlock() == style.statusLamp().getBlock()) {
				assertTrue(boardLamps.contains(pl.rel()), "a status lamp that is not the board's at " + pl.rel());
			}
			assertFalse(s.getBlock() instanceof LanternBlock, "a free-standing lantern at " + pl.rel());
		}
	}

	@Test
	void decorationIsSupported() {
		BuildPlan p = growth().get(2).plan();
		for (Placement pl : p.placements()) {
			BlockState s = pl.state();
			if (s.isAir() || !s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, pl.rel()).isEmpty()) {
				continue;
			}
			BlockPos support;
			if (s.getBlock() instanceof WallSignBlock) {
				support = pl.rel().relative(s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
				Placement behind = p.at(support);
				assertTrue(behind != null && behind.state().isSolidRender(), "wall sign without a solid wall at " + pl.rel());
				continue;
			}
			if (s.getLightEmission() > 0) {
				continue;
			}
			support = pl.rel().below();
			Placement below = p.at(support);
			assertTrue(below != null && !below.state().isAir(), s + " floats at " + pl.rel());
		}
	}

	// ------------------------------------------------------------------ light

	@ParameterizedTest(name = "{0} projects")
	@ValueSource(ints = {4, 10, 20})
	void everyReachableCellHasBlockLightOfAtLeastTen(int n) {
		BuildPlan p = result(n).plan();
		Set<BlockPos> reach = new PlanWalkability(p).reachable(p.anchor(AnchorNames.SPAWN).block());
		assertTrue(reach.size() > 1000, "the town is walkable: " + reach.size());
		assertLit(p, reach, n + " projects");
	}

	/** Block light of a whole plan in {@link TowncenterLight}'s model (cells outside the plan are air). */
	static void assertLit(BuildPlan p, Set<BlockPos> cells, String ctx) {
		Box fp = p.footprint();
		int x0 = fp.minX() - 1;
		int y0 = Math.max(0, fp.minY());
		int z0 = fp.minZ() - 1;
		int nx = fp.maxX() - x0 + 2;
		int ny = fp.maxY() - y0 + 2;
		int nz = fp.maxZ() - z0 + 2;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState[] st = new BlockState[nx * ny * nz];
		byte[] lv = new byte[st.length];
		List<List<Integer>> buckets = new ArrayList<>();
		for (int i = 0; i <= 15; i++) {
			buckets.add(new ArrayList<>());
		}
		for (int i = 0; i < st.length; i++) {
			st[i] = air;
		}
		for (Placement pl : p.placements()) {
			BlockPos c = pl.rel();
			if (c.getY() < y0) {
				continue;
			}
			int i = ((c.getY() - y0) * nz + (c.getZ() - z0)) * nx + (c.getX() - x0);
			st[i] = pl.state();
			int e = pl.state().getLightEmission();
			if (e > 0) {
				lv[i] = (byte) e;
				buckets.get(e).add(i);
			}
		}
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int level = 15; level >= 1; level--) {
			List<Integer> q = buckets.get(level);
			for (int k = 0; k < q.size(); k++) {
				int i = q.get(k);
				if (lv[i] != level) {
					continue;
				}
				int x = i % nx;
				int z = (i / nx) % nz;
				int y = i / (nx * nz);
				for (int[] d : dirs) {
					int xx = x + d[0];
					int yy = y + d[1];
					int zz = z + d[2];
					if (xx < 0 || yy < 0 || zz < 0 || xx >= nx || yy >= ny || zz >= nz) {
						continue;
					}
					int j = (yy * nz + zz) * nx + xx;
					if (TowncenterLight.opaque(st[j])) {
						continue;
					}
					int nl = level - TowncenterLight.damp(st[j]);
					if (nl > lv[j]) {
						lv[j] = (byte) nl;
						buckets.get(nl).add(j);
					}
				}
			}
			q.clear();
		}
		List<String> dark = new ArrayList<>();
		for (BlockPos c : cells) {
			int x = c.getX() - x0;
			int y = c.getY() - y0;
			int z = c.getZ() - z0;
			int i = (y * nz + z) * nx + x;
			int light;
			if (!TowncenterLight.opaque(st[i])) {
				light = lv[i];
			} else {
				light = 0;
				for (int[] d : dirs) {
					int j = ((y + d[1]) * nz + (z + d[2])) * nx + (x + d[0]);
					if (!TowncenterLight.opaque(st[j]) || st[j].getLightEmission() > 0) {
						light = Math.max(light, lv[j] - 1);
					}
				}
			}
			if (light < TowncenterLight.MIN) {
				dark.add(c.toShortString() + "=" + light);
			}
		}
		Collections.sort(dark);
		assertTrue(dark.isEmpty(), ctx + ": " + dark.size() + " reachable cells darker than " + TowncenterLight.MIN + ": "
				+ dark.subList(0, Math.min(20, dark.size())));
	}

	@Test
	void noDirectionIsLeftWithoutAStreetIntoThePlaza() {
		// several streets reach the plaza mayor: the four half streets of cell 0 are built and the hall has doors on all sides
		BuildPlan p = growth().get(0).plan();
		for (String s : new String[] {"n", "s", "w", "e"}) {
			assertTrue(p.modules().stream().anyMatch(m -> m.id().equals("street_0_" + s)), s);
		}
		int doors = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int x = -20; x <= 20; x++) {
				for (int z = -24; z <= 16; z++) {
					if (TowncenterBuilder.hubDoor(x, z) > 0 && TowncenterBuilder.hubWall(x, z) && !TowncenterBuilder.inHub(x + d.getStepX(), z + d.getStepZ())) {
						doors++;
					}
				}
			}
		}
		assertTrue(doors >= 10, "doors of the civic hall: " + doors);
	}
}
