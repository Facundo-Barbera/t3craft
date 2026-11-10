package dev.t3craft.hq;

import dev.t3craft.hq.pack.LayoutPack;
import dev.t3craft.hq.plan.Anchor;
import dev.t3craft.hq.plan.AnchorNames;
import dev.t3craft.hq.plan.Box;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.Placement;
import dev.t3craft.hq.plan.Workstation;
import dev.t3craft.hq.style.HqStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * feat/hq-layout-packs: applies a {@link LayoutPack} to a generated plan. Pure and deterministic (same
 * plan and pack in, equal plan out). Two steps, around {@link HqLighting}:
 * <ol>
 *   <li>{@link #rooms}: before lighting (so a designed room still gets its lamps), the content of every
 *   module the pack has a room for is replaced by the structure's blocks;</li>
 *   <li>{@link #remap}: after lighting, every block whose block is a remapped role, palette field or
 *   block becomes the replacement, keeping the original's orientation properties.</li>
 * </ol>
 * Anchors, desks and their screens never move: their cells are protected from rooms (a room data
 * marker may move a non-desk anchor, see {@link dev.t3craft.hq.pack.RoomTemplate}).
 */
final class PackApply {
	private PackApply() {
	}

	/** The layout a style draws ({@code classic} for the spine styles). */
	static String layoutOf(HqStyle style) {
		return style.ownLayout() ? style.id() : dev.t3craft.hq.layout.HqLayouts.CLASSIC;
	}

	// ------------------------------------------------------------------ rooms

	static BuildPlan rooms(BuildPlan plan, LayoutPack pack) {
		if (pack.rooms().isEmpty()) {
			return plan;
		}
		Set<String> deskAnchors = new HashSet<>();
		Set<BlockPos> fixed = new HashSet<>();
		for (Workstation w : plan.workstations()) {
			deskAnchors.add(w.deskAnchor().name());
			deskAnchors.add(w.screen().name());
			if (w.threadId() != null) {
				deskAnchors.add(AnchorNames.desk(w.threadId()));
				deskAnchors.add(AnchorNames.monitor(w.threadId()));
			}
			fixed.addAll(w.screenCells());
			fixed.add(w.computerPos());
			fixed.add(w.computerPos().below());
			fixed.add(w.deskAnchor().block());
			fixed.add(w.deskAnchor().block().above());
		}
		Map<BlockPos, Placement> cells = new LinkedHashMap<>();
		for (Placement p : plan.placements()) {
			cells.put(p.rel(), p);
		}
		Map<BlockPos, String> extraGroups = new HashMap<>();
		Map<String, Anchor> moved = new LinkedHashMap<>();
		List<BuildPlan.Module> roomModules = new ArrayList<>();
		for (BuildPlan.Module m : plan.modules()) {
			LayoutPack.Room room = pack.rooms().get(m.id());
			if (room == null || m.sparse()) {
				continue;
			}
			roomModules.add(m);
			// 1. markers move anchors (never a desk's)
			for (dev.t3craft.hq.pack.RoomTemplate.Marker mk : room.template().markers()) {
				Anchor old = plan.anchor(mk.anchor());
				if (old == null || deskAnchors.contains(old.name())) {
					continue;
				}
				BlockPos rel = place(m, room, mk.pos());
				if (!m.owns(rel.getX(), rel.getY(), rel.getZ())) {
					continue;
				}
				Direction facing = mk.facing() != null ? room.rotation().rotate(mk.facing()) : old.facing();
				boolean face = old.y() != Math.floor(old.y());
				moved.put(old.name(), face ? Anchor.face(old.name(), rel, facing) : Anchor.at(old.name(), rel, facing));
			}
		}
		// every anchor's cell (and the head room over standing spots) keeps what the generator or the marker put there
		for (Anchor a : plan.anchors().values()) {
			Anchor now = moved.getOrDefault(a.name(), a);
			fixed.add(now.block());
			if (now.y() == Math.floor(now.y())) {
				fixed.add(now.block().above());
			}
		}
		for (BuildPlan.Module m : roomModules) {
			LayoutPack.Room room = pack.rooms().get(m.id());
			for (dev.t3craft.hq.pack.RoomTemplate.Cell c : room.template().cells()) {
				BlockPos rel = place(m, room, c.pos());
				if (fixed.contains(rel) || !m.owns(rel.getX(), rel.getY(), rel.getZ())) {
					continue;
				}
				String owner = plan.groupAt(rel);
				if (owner == null || !owner.equals(m.group())) {
					continue;
				}
				BlockState s = c.state().rotate(room.rotation());
				Placement np = new Placement(rel, s, c.be() == null ? null : c.be().copy());
				if (!cells.containsKey(rel)) {
					extraGroups.put(rel, m.group());
				}
				cells.put(rel, np);
			}
		}
		return plan.rebuilt(new ArrayList<>(cells.values()), moved.values(), extraGroups);
	}

	/** Plan cell of a room's cell {@code p}: turned in the structure's own box, from the module box's minimum corner plus the offset. */
	private static BlockPos place(BuildPlan.Module m, LayoutPack.Room room, BlockPos p) {
		dev.t3craft.hq.pack.RoomTemplate t = room.template();
		BlockPos local = turn(p, room.rotation(), t.sizeX(), t.sizeZ());
		Box b = m.box();
		BlockPos o = room.offset();
		return new BlockPos(b.minX() + o.getX() + local.getX(), b.minY() + o.getY() + local.getY(), b.minZ() + o.getZ() + local.getZ());
	}

	/** {@code p} turned by {@code r} inside a {@code sx × sz} box, staying in the box. */
	static BlockPos turn(BlockPos p, Rotation r, int sx, int sz) {
		return switch (r) {
			case NONE -> p;
			case CLOCKWISE_90 -> new BlockPos(sz - 1 - p.getZ(), p.getY(), p.getX());
			case CLOCKWISE_180 -> new BlockPos(sx - 1 - p.getX(), p.getY(), sz - 1 - p.getZ());
			case COUNTERCLOCKWISE_90 -> new BlockPos(p.getZ(), p.getY(), sx - 1 - p.getX());
		};
	}

	// ------------------------------------------------------------------ materials

	/** Every block a pack turns into another one, for {@code style}: roles, then palette fields, then blocks (later wins). */
	static Map<Block, LayoutPack.Replacement> remapTable(HqStyle style, LayoutPack pack) {
		Map<Block, LayoutPack.Replacement> by = new HashMap<>();
		Map<HqStyle.Role, BlockState> roles = style.palette();
		for (Map.Entry<String, LayoutPack.Replacement> e : pack.roles().entrySet()) {
			BlockState def = roles.get(HqStyle.Role.valueOf(e.getKey()));
			if (def != null && !def.isAir()) {
				put(by, def.getBlock(), e.getValue());
			}
		}
		Map<String, BlockState> pal = PackDefaults.paletteDefaults();
		for (Map.Entry<String, LayoutPack.Replacement> e : pack.palette().entrySet()) {
			BlockState def = pal.get(e.getKey());
			if (def != null && !def.isAir()) {
				put(by, def.getBlock(), e.getValue());
			}
		}
		for (Map.Entry<Block, LayoutPack.Replacement> e : pack.blocks().entrySet()) {
			put(by, e.getKey(), e.getValue());
		}
		return by;
	}

	private static void put(Map<Block, LayoutPack.Replacement> by, Block from, LayoutPack.Replacement o) {
		if (o.state().getBlock() == from && o.fixed().isEmpty()) {
			return; // the code's own block (an exported default): nothing to do
		}
		by.put(from, o);
	}

	static BuildPlan remap(BuildPlan plan, HqStyle style, LayoutPack pack) {
		Map<Block, LayoutPack.Replacement> by = remapTable(style, pack);
		if (by.isEmpty()) {
			return plan;
		}
		return plan.replacing(p -> {
			LayoutPack.Replacement o = by.get(p.state().getBlock());
			if (o == null) {
				return p;
			}
			BlockState s = o.apply(p.state());
			if (s.equals(p.state())) {
				return p;
			}
			return new Placement(p.rel(), s, p.be() != null && s.hasBlockEntity() ? p.be() : null);
		});
	}

	/** Rooms, lighting, materials and the pack hash: what {@link HqGenerator#generate} does with a pack. */
	static BuildPlan withPack(BuildPlan base, HqStyle style, @Nullable LayoutPack pack) {
		if (pack == null) {
			return HqLighting.light(base, style);
		}
		BuildPlan lit = HqLighting.light(rooms(base, pack), style);
		return remap(lit, style, pack).withPackHash(pack.hash());
	}
}
