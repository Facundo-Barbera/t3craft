package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Layout {@code hybrid-terraces} ("Acrópolis en terrazas") through the shared {@link HqStyle} hook:
 * the plan is {@link TerraceTown}, the drawing {@link TerraceGenerator}, the palette cherry.
 *
 * <h2>Allocation ({@link #allocate})</h2>
 * Each project lives in barrios: a barrio takes one solar (a {@link TerraceTown} cell) and has up to
 * {@value #BARRIO_DESKS} desks in three buildings of different sizes: the main hall (desks 0..5),
 * annex B (6..9) and the annex tower C (10..13). Encoded in the shared {@link HqLayout} record as
 * modules {@code rank * 4 + part} (part 0 = hall, 1 = annex B, 2 = tower C; slot = desk within the
 * part). Rules, on canonical (sorted) input, so the result never depends on roster order:
 * <ol>
 *   <li>a thread that had a desk in a barrio of its own project keeps it;</li>
 *   <li>a barrio keeps its solar while one of its desks is taken;</li>
 *   <li>a new thread takes the lowest free desk of its project's barrios (lowest rank first);</li>
 *   <li>when they are full (or the project has none) it opens a barrio on the lowest free solar rank,
 *   so freed interior solares are refilled before the HQ grows outwards; projects in key order;</li>
 *   <li>settled threads go to the archive (the shared shelf rules, {@link HqLayout#shelve}).</li>
 * </ol>
 * {@code allocate(in, allocate(in, null))} equals {@code allocate(in, null)}.
 */
final class TerraceStyle extends HqStyle {
	static final String ID = TerraceGenerator.ID;
	static final int HALL_DESKS = 6;
	static final int ANNEX_DESKS = 4;
	static final int BARRIO_DESKS = HALL_DESKS + 2 * ANNEX_DESKS;
	static final int[] PART_START = {0, HALL_DESKS, HALL_DESKS + ANNEX_DESKS};

	private final Map<Role, BlockState> palette;

	TerraceStyle() {
		Map<Role, BlockState> m = new EnumMap<>(Role.class);
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

	/** Part of a barrio desk number. */
	static int part(int desk) {
		return desk < PART_START[1] ? 0 : desk < PART_START[2] ? 1 : 2;
	}

	/** The solar ranks a layout takes. */
	static TreeSet<Integer> plots(HqLayout layout) {
		TreeSet<Integer> out = new TreeSet<>();
		for (HqLayout.Module m : layout.modules()) {
			out.add(m.index() / 4);
		}
		return out;
	}

	// ------------------------------------------------------------------ layout hook

	@Override
	public HqLayout allocate(HqInput input, @Nullable HqLayout previous) {
		HqLayout prev = previous == null ? HqLayout.EMPTY : previous;
		int plotCount = TerraceTown.get().plots.size();
		// the previous barrios: owner of each solar and desk of each thread
		Map<Integer, String> oldOwner = new HashMap<>();
		Map<String, int[]> oldDesk = new HashMap<>();
		for (HqLayout.Module m : prev.modules()) {
			int plot = m.index() / 4;
			int part = Math.min(2, m.index() % 4);
			oldOwner.put(plot, m.projectKey());
			for (HqLayout.Desk d : m.desks()) {
				oldDesk.put(d.threadId(), new int[] {plot, PART_START[part] + d.slot()});
			}
		}
		TreeMap<Integer, TreeMap<Integer, String>> desks = new TreeMap<>();
		Map<Integer, HqInput.Project> owner = new HashMap<>();
		List<HqInput.Project> projects = new ArrayList<>(input.projects());
		projects.sort(java.util.Comparator.comparing(HqInput.Project::key));
		Map<String, List<String>> fresh = new TreeMap<>();
		for (HqInput.Project p : projects) {
			List<String> threads = new ArrayList<>(p.threads());
			threads.sort(null);
			for (String t : threads) {
				int[] od = oldDesk.get(t);
				if (od != null && p.key().equals(oldOwner.get(od[0])) && od[1] < BARRIO_DESKS) {
					desks.computeIfAbsent(od[0], k -> new TreeMap<>()).put(od[1], t);
					owner.put(od[0], p);
				} else {
					fresh.computeIfAbsent(p.key(), k -> new ArrayList<>()).add(t);
				}
			}
		}
		TreeSet<Integer> used = new TreeSet<>(desks.keySet());
		for (HqInput.Project p : projects) {
			for (String t : fresh.getOrDefault(p.key(), List.of())) {
				boolean seated = false;
				for (Map.Entry<Integer, TreeMap<Integer, String>> d : desks.entrySet()) {
					if (owner.get(d.getKey()) != p || d.getValue().size() >= BARRIO_DESKS) {
						continue;
					}
					d.getValue().put(HqLayout.lowestFree(d.getValue().keySet()), t);
					seated = true;
					break;
				}
				if (!seated) {
					int plot = HqLayout.lowestFree(used);
					if (plot >= plotCount) {
						throw new IllegalStateException("the terraces have no free solar left (" + plotCount + " solares)");
					}
					used.add(plot);
					TreeMap<Integer, String> m = new TreeMap<>();
					m.put(0, t);
					desks.put(plot, m);
					owner.put(plot, p);
				}
			}
		}
		List<HqLayout.Module> mods = new ArrayList<>();
		for (Map.Entry<Integer, TreeMap<Integer, String>> e : desks.entrySet()) {
			HqInput.Project p = owner.get(e.getKey());
			List<List<HqLayout.Desk>> parts = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
			e.getValue().forEach((desk, t) -> {
				int part = part(desk);
				parts.get(part).add(new HqLayout.Desk(desk - PART_START[part], t));
			});
			for (int part = 0; part < 3; part++) {
				if (!parts.get(part).isEmpty()) {
					mods.add(new HqLayout.Module(e.getKey() * 4 + part, p.key(), p.label(), p.envKey(), p.envLabel(), parts.get(part)));
				}
			}
		}
		HqLayout.Shelf shelf = HqLayout.shelve(input, prev);
		return new HqLayout(mods, shelf.archive(), shelf.overflow());
	}

	@Override
	public HqFootprints.Area envelope(HqLayout layout) {
		return TerraceTown.get().envelope(plots(layout));
	}

	@Override
	public int bottom() {
		return 0;
	}

	@Override
	public int top() {
		return TerraceTown.TOP_Y;
	}

	@Override
	public boolean ownLayout() {
		return true;
	}

	@Override
	BuildPlan layoutPlan(HqLayout layout) {
		return new TerraceGenerator(this).generate(layout);
	}

	// ------------------------------------------------------------------ spine pieces: unused

	@Override
	void hub(PlanBuilder.Canvas c, boolean northDoor) {
		throw new UnsupportedOperationException("terraces draw their own temple");
	}

	@Override
	void board(PlanBuilder.Canvas c, SignText sign) {
		throw new UnsupportedOperationException("terraces draw their own board");
	}

	@Override
	void archiveNook(PlanBuilder.Canvas c) {
		throw new UnsupportedOperationException("terraces have a library");
	}

	@Override
	void portico(PlanBuilder.Canvas c) {
		throw new UnsupportedOperationException("terraces have a propylaea");
	}

	@Override
	void spineRow(PlanBuilder.Canvas c, int r, @Nullable SignText eastSign, @Nullable SignText westSign) {
		throw new UnsupportedOperationException("terraces have no spine");
	}

	@Override
	void spineCap(PlanBuilder.Canvas c, Box box) {
		throw new UnsupportedOperationException("terraces have no spine");
	}

	@Override
	void wingEndWall(PlanBuilder.Canvas c, int i, int u, boolean inner) {
		throw new UnsupportedOperationException("terraces have no wings");
	}

	@Override
	void segment(PlanBuilder.Canvas c, int i, int s, DyeColor accent) {
		throw new UnsupportedOperationException("terraces have no wings");
	}

	@Override
	void desk(PlanBuilder.Canvas c, DeskCells cells) {
		throw new UnsupportedOperationException("terraces draw their own desks");
	}
}
