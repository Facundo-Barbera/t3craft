package dev.t3craft.hq;

import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelBarrios;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import dev.t3craft.hq.citadel.CitadelHub;
import dev.t3craft.hq.citadel.CitadelGrounds;
import dev.t3craft.hq.citadel.CitadelFinish;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.SOLID;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.Y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.FH;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.BAMBOO;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;
import static dev.t3craft.hq.citadel.CitadelCanvas.lattice;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

/**
 * Plan of the hybrid layout {@value RingCampusGeometry#ID} ({@link RingCampusStyle}, geometry in
 * {@link RingCampusGeometry}). Pure and deterministic: {@link HqLayout} in, {@link BuildPlan} out.
 *
 * <p>It paints the ground column by column (each ground column belongs to one
 * {@link RingCampusGeometry.Zone}), then the hub, the core (patio, bridges, elevated promenade,
 * ramps), the node rooms and the barrios; then it <b>lights</b> the plan: every cell an NPC can walk
 * to (approximated from the spawn with one-block steps) must get block light ≥ {@value #MIN_LIGHT}
 * from the lights of its own construction group, and where it does not, the generator embeds a light
 * block in the floor under it (or in the wall or ceiling next to it). Lights only ever come from
 * blocks built into floors, walls, ceilings and pillars: there are no loose lanterns.
 *
 * <p>Groups (the construction units): {@code hub}, {@code cloister} (patio, bridges, promenade,
 * ramps, terraces, inner street), {@code archive} (the archive cloister), {@code node_<k>},
 * {@code ring_<m>} (avenues, street and empty lots of ring m), {@code lot_<id>} (a barrio's
 * courtyard, paths, gardens and gatehouse), {@code module_<index>} (one floor of a hall) and
 * {@code grounds} (the lawn). A hall's cells depend only on its own modules, so adding a project
 * never touches the existing halls, nodes, hub or core.
 */
final class RingCampusGenerator {
	static final int MIN_LIGHT = CitadelFinish.MIN_LIGHT;

	private final CitadelCanvas canvas;

	RingCampusGenerator(RingCampusStyle style) {
		this.canvas = new CitadelCanvas(style, HqKit.active());
	}

	BuildPlan generate(HqLayout layout) {
		canvas.layout = layout;
		List<Integer> idx = new ArrayList<>();
		for (HqLayout.Module m : layout.modules()) {
			int lot = RingCampusGeometry.lotOf(m.index());
			if (lot >= 0) {
				idx.add(m.index());
				canvas.lots.computeIfAbsent(lot, k -> new TreeMap<>()).put(m.index() % RingCampusGeometry.LOT_STRIDE, m);
			}
		}
		canvas.rings = RingCampusGeometry.rings(idx);
		int R = RingCampusGeometry.radius(canvas.rings);
		Map<Long, Zone> zs = new HashMap<>();
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				Zone zn = RingCampusGeometry.zone(x, z, canvas.rings);
				if (zn.kind() != Kind.NONE) {
					zs.put(col(x, z), zn);
					if (zn.kind() == Kind.LOT) {
						canvas.lotCols.computeIfAbsent(zn.lot(), k -> new ArrayList<>()).add(col(x, z));
					}
				}
			}
		}
		canvas.zones = zs;
		for (Map.Entry<Long, Zone> e : zs.entrySet()) {
			CitadelGrounds.ground(canvas, colX(e.getKey()), colZ(e.getKey()), e.getValue());
		}
		CitadelHub.hub(canvas);
		CitadelHub.core(canvas);
		for (int k = 0; k < 8; k++) {
			dev.t3craft.hq.citadel.CitadelNodes.node(canvas, RingCampusGeometry.node(k));
		}
		int lotCount = RingCampusGeometry.base(canvas.rings + 1);
		for (int lot = 0; lot < lotCount; lot++) {
			TreeMap<Integer, HqLayout.Module> mods = canvas.lots.get(lot);
			if (mods == null) {
				CitadelGrounds.emptyLot(canvas, lot);
			} else {
				CitadelBarrios.barrio(canvas, lot, mods);
			}
		}
		canvas.anchor(Anchor.at(AnchorNames.SPAWN, CitadelFinish.SPAWN, Direction.NORTH));
		return CitadelFinish.build(canvas);
	}

			}
		}
	}

		}
	}

	/**
		}
	}

	// ------------------------------------------------------------------ barrios

	}
}
