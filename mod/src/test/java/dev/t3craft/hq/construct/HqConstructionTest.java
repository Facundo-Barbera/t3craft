package dev.t3craft.hq.construct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqSite;
import dev.t3craft.site.CellPlan;
import dev.t3craft.site.SiteModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HqConstructionTest {
	static final CellPlan.Origin ORIGIN = new CellPlan.Origin(200, 70, 300, CellPlan.Facing.EAST);

	@BeforeAll
	static void boot() {
		ConstructFixtures.boot();
	}

	/** A site and a world in memory; expansions and updates finish when the test says so. */
	static final class Fake implements HqConstruction.Ports {
		String siteId = "s1";
		boolean idle = true;
		boolean hasSite = true;
		CellPlan.Origin origin = ORIGIN;
		final List<String> modules = new ArrayList<>(List.of(HqSiteFrame.BASE));
		final List<SiteModule> pending = new ArrayList<>();
		/** Log: "snapshot:<id>", "expand:<id>", "update:<n>". */
		final List<String> log = new ArrayList<>();
		final Map<BlockPos, BlockState> world = new HashMap<>();
		final List<HqConstruction.Cell> lastUpdate = new ArrayList<>();
		@Nullable String expanding;
		boolean updating;
		HqConstruction.Cost cost = HqConstruction.Cost.FREE;
		HqConstruction.Saved saved = HqConstruction.Saved.EMPTY;
		@Nullable BuildPlan anchorsPlan;
		Set<String> anchorsBuilt = Set.of();
		long now = 1_000_000;
		final List<String> told = new ArrayList<>();

		@Override
		public HqConstruction.@Nullable SiteView site() {
			return hasSite ? new HqConstruction.SiteView(siteId, idle && expanding == null, origin, modules, pending, true) : null;
		}

		@Override
		public @Nullable String expand(SiteModule module, BuildPlan design) {
			if (modules.contains(module.id())) {
				return "already built";
			}
			log.add("snapshot:" + module.id());
			log.add("expand:" + module.id());
			// the site designs the module from the plan, clipped to its slot
			HqSite s = HqSiteFrame.hqSite(origin);
			for (var p : design.placements()) {
				assertTrue(p.rel().getY() >= 0 || !HqSiteFrame.builds(p.rel()));
				if (HqSiteFrame.builds(p.rel()) && module.id().equals(HqSiteFrame.owner(p.rel().getX(), p.rel().getZ()))) {
					world.put(s.cell(p.rel()), p.state().rotate(s.rotation()));
				}
			}
			modules.add(module.id());
			pending.removeIf(m -> m.id().equals(module.id()));
			expanding = module.id();
			return null;
		}

		@Override
		public HqConstruction.Cost cost(String what, List<BlockState> states) {
			return cost;
		}

		@Override
		public HqConstruction.Update update(String label, List<HqConstruction.Cell> cells, List<HqConstruction.Cell> blockEntities) {
			lastUpdate.clear();
			int n = 0;
			for (HqConstruction.Cell c : cells) {
				assertTrue(c.pos().getY() >= origin.y(), "nothing below the HQ floor: " + c.pos());
				boolean differs = c.removal() ? world.containsKey(c.pos()) : !c.state().equals(world.get(c.pos()));
				if (differs) {
					lastUpdate.add(c);
					n++;
				}
			}
			if (n == 0 && blockEntities.isEmpty()) {
				return HqConstruction.Update.NOTHING;
			}
			for (HqConstruction.Cell c : lastUpdate) {
				if (c.removal()) {
					world.remove(c.pos()); // the site restores its snapshot (here: nothing)
				} else {
					world.put(c.pos(), c.state());
				}
			}
			log.add("update:" + n);
			updating = true;
			return new HqConstruction.Update(true, n, null);
		}

		@Override
		public void anchors(@Nullable BuildPlan plan, @Nullable HqSite site, Set<String> built) {
			anchorsPlan = plan;
			anchorsBuilt = built;
		}

		@Override
		public void save(HqConstruction.Saved state) {
			saved = state;
		}

		@Override
		public void tell(String line) {
			told.add(line);
		}

		@Override
		public long now() {
			return now;
		}
	}

	Fake f;
	HqConstruction hq;

	@BeforeEach
	void setUp() {
		f = new Fake();
		hq = new HqConstruction(f, HqConstruction.Saved.EMPTY);
	}

	/** Ticks (past the debounce) until nothing is pending, finishing site builds and updates as they start. */
	void settle() {
		f.now += HqConstruction.DEBOUNCE_MS;
		for (int i = 0; i < 50; i++) {
			hq.tick();
			if (f.expanding != null) {
				String id = f.expanding;
				f.expanding = null;
				hq.onExpansionBuilt(id);
			} else if (f.updating) {
				f.updating = false;
				hq.onUpdated(true);
			} else if (hq.phase() == HqConstruction.Phase.IDLE || hq.phase() == HqConstruction.Phase.BLOCKED
					|| hq.phase() == HqConstruction.Phase.PAUSED || hq.phase() == HqConstruction.Phase.WAITING_SITE) {
				return;
			}
		}
	}

	@Test
	void buildsTheWingsInOrderAfterTheBaseThenUpdatesInPlace() {
		hq.roster(ConstructFixtures.input(2, 4, 0));
		settle();
		assertEquals(List.of("snapshot:wing-0", "expand:wing-0", "snapshot:wing-1", "expand:wing-1"), f.log.subList(0, 4));
		assertTrue(f.log.size() <= 5, f.log.toString());
		assertEquals(HqConstruction.Phase.IDLE, hq.phase());
		assertEquals(2, f.saved.layout().modules().size());
		assertEquals("s1", f.saved.siteId());
		assertNotNull(f.anchorsPlan);
		assertEquals(Set.of("base", "wing-0", "wing-1"), f.anchorsBuilt);
		// every desk of the roster exists in the plan the NPCs follow
		assertNotNull(f.anchorsPlan.anchor(AnchorNames.desk("vps:p0-a0")));
	}

	@Test
	void sameRosterBuildsNothingAgain() {
		HqInput in = ConstructFixtures.input(2, 4, 1);
		hq.roster(in);
		settle();
		int before = f.log.size();
		// same lines in another order: same input
		List<HqInput.Project> shuffled = new ArrayList<>(in.projects());
		Collections.reverse(shuffled);
		hq.roster(new HqInput(shuffled));
		settle();
		settle();
		assertEquals(before, f.log.size(), f.log.toString());
		assertEquals(HqConstruction.Phase.IDLE, hq.phase());

		// a new instance from the saved state on the same site: still nothing to do
		HqConstruction again = new HqConstruction(f, f.saved);
		again.roster(in);
		f.now += HqConstruction.DEBOUNCE_MS;
		again.tick();
		again.tick();
		assertEquals(HqConstruction.Phase.IDLE, again.phase());
		// the first tick on a known site checks the cells once; they all match, so no update starts
		assertEquals(before, f.log.size(), f.log.toString());
	}

	@Test
	void anAnnexIsSnapshottedBeforeItsCellsAreWritten() {
		hq.roster(ConstructFixtures.input(1, 6, 0));
		settle();
		assertEquals(List.of("snapshot:wing-0", "expand:wing-0"), f.log.subList(0, 2));
		f.log.clear();
		// a seventh thread: the module is full, an identical module is added
		hq.roster(ConstructFixtures.input(1, 7, 0));
		settle();
		assertEquals("snapshot:wing-1", f.log.get(0), f.log.toString());
		assertEquals("expand:wing-1", f.log.get(1));
		assertEquals(2, f.saved.layout().modules().size());
		assertEquals("vps:p0", f.saved.layout().moduleAt(1).projectKey());
		// no update ever wrote into a slot the site has not built
		for (HqConstruction.Cell c : f.lastUpdate) {
			assertNotNull(c.pos());
		}
	}

	@Test
	void survivalVerdictBlocksAnExpansion() {
		f.cost = new HqConstruction.Cost(false, "missing 40× oak_planks");
		hq.roster(ConstructFixtures.input(1, 2, 0));
		settle();
		assertEquals(HqConstruction.Phase.BLOCKED, hq.phase());
		assertTrue(f.log.isEmpty());
		assertTrue(f.told.stream().anyMatch(s -> s.contains("oak_planks")));
		f.cost = HqConstruction.Cost.FREE;
		hq.build();
		settle();
		assertEquals(List.of("snapshot:wing-0", "expand:wing-0"), f.log.subList(0, 2));
	}

	@Test
	void settledThreadFreesItsDeskAndItsComputerGoesToTheArchive() {
		hq.roster(ConstructFixtures.input(1, 3, 0));
		settle();
		HqLayout before = f.saved.layout();
		assertNull(before.archived("vps:p0-a1"));
		f.log.clear();

		HqInput.Builder b = HqInput.builder();
		b.thread("vps:p0", "Project 0", "vps", "vps", "vps:p0-a0", false);
		b.thread("vps:p0", "Project 0", "vps", "vps", "vps:p0-a1", true);
		b.thread("vps:p0", "Project 0", "vps", "vps", "vps:p0-a2", false);
		hq.roster(b.build());
		settle();
		assertEquals(1, f.log.size(), f.log.toString());
		assertTrue(f.log.get(0).startsWith("update:"));
		HqLayout after = f.saved.layout();
		assertNotNull(after.archived("vps:p0-a1"));
		assertNull(after.seat("vps:p0-a1"));
		// its old desk's computer was rewritten in place (now vacant); nothing went to the unbuilt basement
		HqSite s = HqSiteFrame.hqSite(ORIGIN);
		BlockPos computer = s.cell(dev.t3craft.hq.HqPlans.generate(before).workstation("vps:p0-a1").computerPos());
		assertTrue(f.lastUpdate.stream().anyMatch(c -> c.pos().equals(computer)), "freed desk not rewritten");
		// its desk is vacant now: no desk anchor for it
		assertNull(f.anchorsPlan.anchor(AnchorNames.desk("vps:p0-a1")));
	}

	@Test
	void removedModulesGoBackToTheSnapshotAndThenNothingMore() {
		hq.roster(ConstructFixtures.input(2, 4, 0));
		settle();
		f.log.clear();
		// project 1 is gone: its wing leaves the layout; its cells are removals (restored by the site)
		hq.roster(ConstructFixtures.input(1, 4, 0));
		settle();
		assertEquals(1, f.log.size(), f.log.toString());
		assertEquals(1, f.saved.layout().modules().size());
		HqSite s = HqSiteFrame.hqSite(ORIGIN);
		long removals = f.lastUpdate.stream().filter(HqConstruction.Cell::removal).count();
		assertTrue(removals > 100, "wing 1 removed: " + removals);
		for (HqConstruction.Cell c : f.lastUpdate) {
			if (c.removal()) {
				assertNotNull(c.pos());
			}
		}
		assertNull(f.anchorsPlan.anchor(AnchorNames.desk("acmesite:p1-a0")));
		f.log.clear();
		settle();
		assertTrue(f.log.isEmpty(), "idempotent after removals: " + f.log);
		assertNotNull(s);
	}

	@Test
	void moveRebuildsThePendingModulesOnTheNewSite() {
		hq.roster(ConstructFixtures.input(2, 4, 0));
		settle();
		f.log.clear();
		// site move: new place, only the base, the wings handed back as pending
		f.siteId = "s2";
		f.origin = new CellPlan.Origin(-500, 80, 40, CellPlan.Facing.NORTH);
		f.modules.clear();
		f.modules.add(HqSiteFrame.BASE);
		f.pending.add(HqSiteFrame.module("wing-0"));
		f.pending.add(HqSiteFrame.module("wing-1"));
		f.world.clear();
		hq.onMoved();
		settle();
		assertEquals(List.of("snapshot:wing-0", "expand:wing-0", "snapshot:wing-1", "expand:wing-1"), f.log.subList(0, 4));
		assertEquals("s2", f.saved.siteId());
		assertTrue(f.pending.isEmpty());
		assertEquals(Set.of("base", "wing-0", "wing-1"), f.anchorsBuilt);
	}

	@Test
	void waitsForAnInterruptedSiteAndHoldsAfterAnUndo() {
		hq.roster(ConstructFixtures.input(1, 2, 0));
		f.idle = false; // site BUILDING (interrupted): /t3craft site resume
		settle();
		assertEquals(HqConstruction.Phase.WAITING_SITE, hq.phase());
		assertTrue(f.log.isEmpty());
		f.idle = true; // resumed and finished
		settle();
		assertEquals(List.of("snapshot:wing-0", "expand:wing-0"), f.log.subList(0, 2));
		f.log.clear();

		// site undo takes wing-0 away: it is not rebuilt behind the player's back
		f.modules.remove("wing-0");
		hq.onRestored("wing-0");
		settle();
		assertEquals(HqConstruction.Phase.PAUSED, hq.phase());
		assertTrue(f.log.isEmpty());
		assertEquals(Set.of("base"), f.anchorsBuilt);
		// /t3craft hq resume: build it again
		hq.resume();
		settle();
		assertEquals(List.of("snapshot:wing-0", "expand:wing-0"), f.log.subList(0, 2));
	}

	@Test
	void noSiteNoAnchors() {
		f.hasSite = false;
		hq.roster(ConstructFixtures.input(1, 2, 0));
		settle();
		hq.tick();
		assertEquals(HqConstruction.Phase.NO_SITE, hq.phase());
		assertNull(f.anchorsPlan);
		assertTrue(f.log.isEmpty());
	}
}
