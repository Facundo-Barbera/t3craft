package dev.t3craft.hq;

import dev.t3craft.hq.RingCampusGeometry.Dims;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.MapGenerator;
import dev.t3craft.mapgen.MapLayout;
import dev.t3craft.mapgen.Slot;
import dev.t3craft.mapgen.TerrainShape;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The citadel ({@value RingCampusGeometry#ID}) as a {@link MapGenerator}: the same geometry the plan painter
 * ({@link RingCampusGenerator}) uses, read as a <b>shape</b> (the zones of {@link RingCampusGeometry#zone}) plus
 * <b>slots</b> where the pieces stand: the hub, the eight ring-1 nodes, and for every open lot its courtyard, its
 * gatehouse, its three halls and the lot itself. Frame: the HQ frame (origin = hub floor centre at ground level,
 * entrance south). Pure and deterministic, no blocks.
 *
 * <p>Slots mirror the painter's places one to one (ids: {@code hub}, {@code node_k}, {@code lot_n},
 * {@code lot_n_court}, {@code lot_n_gate}, {@code lot_n_hall_h}, {@code spawn}); a lot's halls are children of the
 * lot slot, and a slot is tagged {@code occupied} when a module of the roster stands in it. The painter still
 * draws the blocks of those places itself (the citadel's own pieces are baked into {@code hq.citadel.*}); moving
 * them behind {@link dev.t3craft.mapgen.PieceSource} is the piece catalog's job.
 */
public final class CitadelMapGenerator implements MapGenerator<HqLayout> {
	@Override
	public String id() {
		return RingCampusGeometry.ID;
	}

	@Override
	public MapLayout generate(HqLayout layout) {
		List<Integer> idx = new ArrayList<>();
		Set<Integer> halls = new HashSet<>();
		Set<Integer> lotsUsed = new HashSet<>();
		for (HqLayout.Module m : layout.modules()) {
			int lot = RingCampusGeometry.lotOf(m.index());
			if (lot >= 0) {
				idx.add(m.index());
				lotsUsed.add(lot);
				halls.add(lot * RingCampusGeometry.HALLS + RingCampusGeometry.hallOf(m.index()));
			}
		}
		int rings = RingCampusGeometry.rings(idx);
		Dims d = RingCampusGeometry.dims();
		List<Slot> slots = new ArrayList<>();
		List<MapLayout.Link> links = new ArrayList<>();
		String group = "citadel";

		slots.add(new Slot("hub", Slot.Role.CENTRE, new BlockPos(0, 0, 0), Direction.SOUTH, d.hubX(), d.hubX(), d.podium + d.sy(17),
				group, null, Set.of()));
		for (int k = 0; k < 8; k++) {
			Node n = RingCampusGeometry.node(k);
			int half = (int) Math.ceil(n.size());
			Direction out = RingCampusGeometry.cardinal(k * Math.PI / 4);
			slots.add(new Slot("node_" + k, Slot.Role.NODE, new BlockPos(n.cx(), RingCampusGeometry.level(1), n.cz()), out, half, half,
					d.sy(12), group, null, Set.of(n.kind().name().toLowerCase(java.util.Locale.ROOT))));
		}
		int lotCount = RingCampusGeometry.base(rings + 1);
		for (int id = 0; id < lotCount; id++) {
			lotSlots(id, lotsUsed.contains(id), halls, d, group, slots);
			links.add(new MapLayout.Link("hub", "lot_" + id, MapLayout.LinkKind.SOLID));
		}
		BlockPos spawn = RingCampusGeometry.spawn();
		slots.add(new Slot("spawn", Slot.Role.SPAWN, spawn, Direction.NORTH, 0, 0, 3, group, null, Set.of()));
		return new MapLayout(id(), 0L, new Shape(rings), slots, links, spawn);
	}

	private static void lotSlots(int id, boolean occupied, Set<Integer> halls, Dims d, String group, List<Slot> out) {
		Lot lot = RingCampusGeometry.lot(id);
		int y = RingCampusGeometry.level(lot.ring());
		String lotId = "lot_" + id;
		Set<String> tags = new HashSet<>();
		tags.add("ring_" + lot.ring());
		if (occupied) {
			tags.add("occupied");
		}
		BlockPos centre = lot.centre().atY(y);
		out.add(new Slot(lotId, Slot.Role.MODULE, centre, lot.out(), lot.reach(), lot.reach(), 2 * d.floorH + d.s(9), group, null, tags));
		out.add(new Slot(lotId + "_court", Slot.Role.PLAZA, centre, lot.out(), lot.court(), lot.court(), 3, group, lotId, Set.of()));
		out.add(roomSlot(lotId + "_gate", Slot.Role.GATE, lot.gate(), y, d.floorH, group, lotId, Set.of()));
		for (int h = 0; h < RingCampusGeometry.HALLS; h++) {
			Set<String> t = new HashSet<>();
			t.add("hall_" + h);
			if (halls.contains(id * RingCampusGeometry.HALLS + h)) {
				t.add("occupied");
			}
			out.add(roomSlot(lotId + "_hall_" + h, Slot.Role.MODULE, lot.hall(h), y, 2 * d.floorH, group, lotId, t));
		}
	}

	/** A room as a slot: centred on its interior, facing out of its door wall (the wall at {@code u = -1}). */
	private static Slot roomSlot(String id, Slot.Role role, Room r, int y, int height, String group, String parent, Set<String> tags) {
		BlockPos centre = r.cell((r.d() - 1) / 2, r.c(), y);
		return new Slot(id, role, centre, r.back().getOpposite(), (r.w() - 1) / 2, r.d() / 2, height, group, parent, tags);
	}

	/** The zones of the open rings: a column has ground wherever the painter assigns it a zone. */
	private record Shape(int rings) implements TerrainShape {
		@Override
		public Area bounds() {
			int r = RingCampusGeometry.radius(rings);
			return new Area(-r, -r, r, r);
		}

		@Override
		public @Nullable Span column(int x, int z) {
			Zone zone = RingCampusGeometry.zone(x, z, rings);
			if (zone.kind() == Kind.NONE) {
				return null;
			}
			int top = RingCampusGeometry.level(zone.ring());
			return new Span(top, top);
		}
	}
}
