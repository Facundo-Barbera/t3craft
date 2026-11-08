package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * layout/village through the shared {@link HqStyle} layout hook: the village keeps its own record
 * ({@link VillageLayout}) and generator ({@link VillageGenerator}); this adapter stores a village
 * allocation in the shared {@link HqLayout} record, so the demo marker, diffs, bindings and NPC
 * anchors work as for every other layout.
 *
 * <p>Encoding (lossless for what the village keeps): hamlet on plot {@code p} = up to three modules
 * at {@code p * 4 + part} (part 0 = the main building, desks 0..5; part 1 = annex B, desks 6..9 as
 * slots 0..3; part 2 = annex C, desks 10..13 as slots 0..3). Singular rooms (only fed by synthetic
 * rosters; the live roster sends every thread to work) use {@code ROOMS + room * 4 + part} with
 * project key {@code @room:<name>}. Archived threads are the {@link HqLayout#archive} plus its
 * overflow (the library's terminal lists them all).
 */
final class VillageStyle extends HqStyle {
	static final String ID = VillageGenerator.ID;
	/** First module index of the singular rooms. */
	static final int ROOMS = 100_000;
	private static final String ROOM_PREFIX = "@room:";
	private static final int[] PART_START = {0, VillageLayout.MAIN_DESKS, VillageLayout.MAIN_DESKS + VillageLayout.ANNEX_B_DESKS};

	private final Map<Role, BlockState> palette;

	VillageStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
		// feat/hq-layouts: one visual style for every layout, cherry (the village keeps its own per-hamlet materials)
		m.putAll(new CherryStyle().palette());
		palette = Collections.unmodifiableMap(m);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Map<Role, BlockState> palette() {
		return palette;
	}

	@Override
	BlockState bench(Direction back) {
		return palette.get(Role.BENCH).setValue(BlockStateProperties.HORIZONTAL_FACING, back);
	}

	// ------------------------------------------------------------------ layout hook

	@Override
	public HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		HqLayout prev = previous == null ? HqLayout.EMPTY : previous;
		VillageLayout v = VillageLayout.allocate(new VillageLayout.Input(input), decode(prev));
		HqLayout.Shelf shelf = HqLayout.shelve(input, prev);
		return encode(v, shelf.archive(), shelf.overflow());
	}

	@Override
	public HqFootprints.Area envelope(HqLayout layout) {
		return VillageFootprints.envelope(decode(layout).plots().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
	}

	@Override
	public int bottom() {
		return VillageFootprints.bottom();
	}

	@Override
	public int top() {
		return VillageFootprints.top();
	}

	@Override
	public boolean ownLayout() {
		return true;
	}

	@Override
	BuildPlan layoutPlan(HqLayout layout) {
		return new VillageGenerator().generate(decode(layout));
	}

	// ------------------------------------------------------------------ encoding

	static HqLayout encode(VillageLayout v, List<HqLayout.Archived> archive, List<String> overflow) {
		List<HqLayout.Module> mods = new ArrayList<>();
		for (VillageLayout.Hamlet h : v.hamlets()) {
			List<List<HqLayout.Desk>> parts = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
			for (VillageLayout.Seat s : h.desks()) {
				int part = s.slot() < PART_START[1] ? 0 : s.slot() < PART_START[2] ? 1 : 2;
				parts.get(part).add(new HqLayout.Desk(s.slot() - PART_START[part], s.threadId()));
			}
			for (int part = 0; part < 3; part++) {
				if (!parts.get(part).isEmpty()) {
					mods.add(new HqLayout.Module(h.plot() * 4 + part, h.projectKey(), h.label(), h.envKey(), h.envLabel(), parts.get(part)));
				}
			}
		}
		for (Map.Entry<VillageLayout.Room, List<VillageLayout.Seat>> e : v.rooms().entrySet()) {
			List<List<HqLayout.Desk>> parts = new ArrayList<>();
			for (VillageLayout.Seat s : e.getValue()) {
				int part = s.slot() / HqLayout.DESKS;
				while (parts.size() <= part) {
					parts.add(new ArrayList<>());
				}
				parts.get(part).add(new HqLayout.Desk(s.slot() % HqLayout.DESKS, s.threadId()));
			}
			for (int part = 0; part < parts.size(); part++) {
				if (!parts.get(part).isEmpty()) {
					String key = ROOM_PREFIX + e.getKey().name();
					mods.add(new HqLayout.Module(ROOMS + e.getKey().ordinal() * 4 + part, key, key, "", "", parts.get(part)));
				}
			}
		}
		// threads the village archived that the shelf does not hold (cannot happen with a live roster) go to the overflow
		List<String> over = new ArrayList<>(overflow);
		java.util.Set<String> shelved = new java.util.HashSet<>(overflow);
		archive.forEach(a -> shelved.add(a.threadId()));
		for (String t : v.archived()) {
			if (!shelved.contains(t)) {
				over.add(t);
			}
		}
		return new HqLayout(mods, archive, over);
	}

	static VillageLayout decode(HqLayout l) {
		Map<Integer, List<VillageLayout.Seat>> desks = new java.util.TreeMap<>();
		Map<Integer, HqLayout.Module> first = new java.util.TreeMap<>();
		Map<VillageLayout.Room, List<VillageLayout.Seat>> rooms = new EnumMap<>(VillageLayout.Room.class);
		for (HqLayout.Module m : l.modules()) {
			int part = m.index() % 4;
			if (m.index() >= ROOMS) {
				VillageLayout.Room r = VillageLayout.Room.values()[(m.index() - ROOMS) / 4];
				for (HqLayout.Desk d : m.desks()) {
					rooms.computeIfAbsent(r, k -> new ArrayList<>()).add(new VillageLayout.Seat(part * HqLayout.DESKS + d.slot(), d.threadId()));
				}
				continue;
			}
			int plot = m.index() / 4;
			first.putIfAbsent(plot, m);
			for (HqLayout.Desk d : m.desks()) {
				desks.computeIfAbsent(plot, k -> new ArrayList<>()).add(new VillageLayout.Seat(PART_START[Math.min(part, 2)] + d.slot(), d.threadId()));
			}
		}
		List<VillageLayout.Hamlet> hamlets = new ArrayList<>();
		first.forEach((plot, m) -> hamlets.add(new VillageLayout.Hamlet(plot, m.projectKey(), m.label(), m.envKey(), m.envLabel(),
				desks.getOrDefault(plot, List.of()))));
		List<String> archived = new ArrayList<>(l.overflow());
		l.archive().forEach(a -> archived.add(a.threadId()));
		return new VillageLayout(hamlets, rooms, archived);
	}

	// ------------------------------------------------------------------ spine pieces: unused (VillageGenerator draws everything)

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		throw new UnsupportedOperationException("village draws its own hub");
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		throw new UnsupportedOperationException("village draws its own board");
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		throw new UnsupportedOperationException("village has a library");
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
		throw new UnsupportedOperationException("village has no portico");
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
		throw new UnsupportedOperationException("village has no spine");
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		throw new UnsupportedOperationException("village has no spine");
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		throw new UnsupportedOperationException("village has no wings");
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		throw new UnsupportedOperationException("village has no wings");
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
		throw new UnsupportedOperationException("village draws its own desks");
	}
}
