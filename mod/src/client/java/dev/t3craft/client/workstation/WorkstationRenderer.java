package dev.t3craft.client.workstation;

import com.mojang.math.Transformation;
import dev.t3craft.blocks.T3Blocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Puts the workstations in the world with client-only entities (never known to any server, nothing
 * registered, no new block):
 * <ul>
 *   <li>a FIXED {@link Display.TextDisplay} screen at {@code screenPos} facing the desk, full-bright,
 *       background tinted by the status;</li>
 *   <li>only while the computer block is air (no HQ/builder block there yet): a
 *       {@link Display.BlockDisplay} "computer" prop chosen by {@link StationProp} (a
 *       {@code t3craft:monitor} when the blocks module is on, a small black-concrete base in
 *       GUI-only mode; never a technical block like an observer) and an {@link Interaction} hitbox so the player still has something to right-click. Over a real
 *       block neither exists and the click goes through {@code UseBlockCallback}.</li>
 * </ul>
 *
 * <p><b>Entity ids</b> count down from {@link #ID_BASE} = −5,000,000 (NpcRegistry uses −1,000,000…,
 * builders −2,000,000…, BehaviorDirector −3,000,000…; see {@code ClientEntityIds}). Each owner only
 * sweeps its own range; still, each tick checks {@link Entity#isRemoved()} and rebuilds a station
 * whose entities vanished, and every {@code SWEEP_TICKS} our range is swept for orphans. Entities are kept
 * by reference and never looked up by id with a cast (hot-swap safe).
 *
 * <p>Entities are created once per station and only re-created when the placement moves or they
 * were removed; text is pushed only when {@link WorkstationCache} says the screen changed.
 */
final class WorkstationRenderer {
	static final int ID_BASE = -5_000_000;
	private static final int LINE_WIDTH_PX = 170;
	private static final float HITBOX_HEIGHT = 1.75f;
	/** Re-check whether the computer block is air this often (ticks). */
	private static final int BLOCK_CHECK_TICKS = 20;
	/** fix/hq-build-stutter: stations (screen, prop, hitbox) created per client tick. */
	static final int SPAWNS_PER_TICK = 3;
	/** Discard entities of our id range that no live station owns this often (ticks; also on the first tick). */
	private static final int SWEEP_TICKS = 20 * 10;

	private static final class Station {
		final String key;
		final StationPlacement placement;
		final Display.TextDisplay screen;
		Display.@Nullable BlockDisplay prop;
		StationProp propKind = StationProp.NONE;
		@Nullable Interaction hitbox;
		boolean archived;
		/** The computer block draws its own screen (e.g. a t3craft monitor): our TextDisplay stays blank. */
		boolean suppressed;
		/** World text: the player is beyond the monitor's level-of-detail distance. */
		boolean far;
		/** Blank right now (suppressed, far or the source switched off). */
		boolean blank;

		Station(String key, StationPlacement placement, Display.TextDisplay screen) {
			this.key = key;
			this.placement = placement;
			this.screen = screen;
		}

		boolean broken() {
			return screen.isRemoved() || (prop != null && prop.isRemoved()) || (hitbox != null && hitbox.isRemoved());
		}
	}

	private final Map<String, Station> stations = new LinkedHashMap<>();
	private final Map<Long, String> byComputer = new HashMap<>();
	private final Map<Integer, String> byEntity = new HashMap<>();
	private int nextId = ID_BASE;
	private int tick;
	/** One-shot sweep of stale props left by older builds (observers) in our id range. */
	private boolean swept;
	/** Computer blocks that draw their own screen; default none (vanilla blocks never do). */
	private Predicate<BlockState> selfDisplay = state -> false;
	/** World text: the viewer (eye) and the hide distance; NaN = unknown (never far). */
	private double viewX = Double.NaN;
	private double viewY;
	private double viewZ;
	private double hideBeyond;
	private boolean screensOn = true;
	/** Extra blocks before a far screen comes back (as the monitor's own LOD hysteresis). */
	static final double FAR_HYSTERESIS = 2;

	/**
	 * World text (docs/features/world-text.md): where the player looks from, the distance beyond which
	 * the screens hide ({@code blocks.monitor.nearDistance}, 0 = never) and {@code labels.hologram.workstationScreens}.
	 */
	void viewer(double x, double y, double z, double hideBeyond, boolean on) {
		viewX = x;
		viewY = y;
		viewZ = z;
		this.hideBeyond = hideBeyond;
		this.screensOn = on;
	}

	/** Far test with hysteresis: a far screen comes back only {@link #FAR_HYSTERESIS} blocks closer. */
	static boolean far(double distSq, double hideBeyond, boolean wasFar) {
		if (!(hideBeyond > 0)) {
			return false;
		}
		double d = wasFar ? hideBeyond : hideBeyond + FAR_HYSTERESIS;
		return distSq > d * d;
	}

	/** See {@link WorkstationClient#setScreenSuppressor}. Null = none. */
	void setSelfDisplay(@Nullable Predicate<BlockState> p) {
		selfDisplay = p == null ? state -> false : p;
	}

	/** Thread key whose computer block is at {@code pos}, or null. */
	@Nullable String stationAt(BlockPos pos) {
		return byComputer.get(StationPlacement.blockKey(pos.getX(), pos.getY(), pos.getZ()));
	}

	/** Thread key for one of our entities (screen, prop or hitbox), or null. */
	@Nullable String stationFor(Entity e) {
		return byEntity.get(e.getId());
	}

	@Nullable StationPlacement placement(String key) {
		Station s = stations.get(key);
		return s == null ? null : s.placement;
	}

	Set<String> keys() {
		return Set.copyOf(stations.keySet());
	}

	int size() {
		return stations.size();
	}

	/**
	 * One client tick. {@code threads}: every thread the registry shows (also archived ones, so the
	 * placement source sees the same set as the NPC layout).
	 */
	void tick(ClientLevel lvl, List<WorkstationThread> threads, WorkstationPlacementSource source, WorkstationCache cache, long nowMs) {
		tick++;
		if (!swept) {
			sweepStaleProps(lvl);
			swept = true;
		}
		source.prepare(threads);
		long t0 = System.nanoTime();
		Set<String> alive = new HashSet<>();
		boolean checkBlocks = tick % BLOCK_CHECK_TICKS == 0;
		int spawnedNow = 0;
		for (WorkstationThread t : threads) {
			StationPlacement p = source.placement(t);
			if (p == null) {
				continue;
			}
			alive.add(t.key());
			Station s = stations.get(t.key());
			boolean created = false;
			boolean needs = s == null || !s.placement.equals(p) || s.broken();
			if (needs && spawnedNow >= SPAWNS_PER_TICK) {
				// fix/hq-build-stutter: a few stations per tick; this one (old place kept meanwhile) next tick
				if (s == null) {
					continue;
				}
				needs = false;
			}
			if (needs) {
				spawnedNow++;
				if (s != null) {
					discard(lvl, s);
				}
				s = spawn(lvl, t.key(), p);
				stations.put(t.key(), s);
				created = true;
			}
			s.archived = t.archived();
			StationScreen screen = cache.update(t, nowMs);
			if (created && screen == null) {
				screen = cache.screen(t.key());
			}
			if (created || checkBlocks) {
				syncProp(lvl, s);
				s.suppressed = selfDisplay.test(lvl.getBlockState(new BlockPos(p.computerX(), p.computerY(), p.computerZ())));
			}
			if (!Double.isNaN(viewX)) {
				double dx = p.screenX() - viewX;
				double dy = p.screenY() - viewY;
				double dz = p.screenZ() - viewZ;
				s.far = far(dx * dx + dy * dy + dz * dz, hideBeyond, s.far);
			}
			boolean blank = s.suppressed || s.far || !screensOn;
			if (blank != s.blank || created) {
				s.blank = blank;
				if (blank) {
					s.screen.setText(Component.empty());
					dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SET_TEXT);
					s.screen.setBackgroundColor(0);
					screen = null;
				} else if (screen == null) {
					screen = cache.screen(t.key()); // back in range: the current text
				}
			}
			if (screen != null && !s.blank) {
				apply(s.screen, screen);
			}
		}
		for (String key : new ArrayList<>(stations.keySet())) {
			if (!alive.contains(key)) {
				discard(lvl, stations.remove(key));
			}
		}
		Set<String> known = new HashSet<>();
		threads.forEach(t -> known.add(t.key()));
		cache.retain(known);
		if (tick % SWEEP_TICKS == 1) {
			sweep(lvl);
		}
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_STATIONS, t0);
	}

	/**
	 * fix/hq-stale-stations: removes every entity of the workstation id range
	 * ({@link dev.t3craft.client.ClientEntityIds#WORKSTATION}) that no live station owns (a station of
	 * a thread that released it, or left over by an earlier renderer after a hot-swap or reload).
	 * Returns how many were discarded.
	 */
	int sweep(ClientLevel lvl) {
		List<Integer> present = new ArrayList<>();
		for (Entity e : lvl.entitiesForRendering()) {
			present.add(e.getId());
		}
		List<Integer> orphans = dev.t3craft.client.ClientEntityIds.WORKSTATION.orphans(present, byEntity.keySet());
		orphans.forEach(id -> lvl.removeEntity(id, Entity.RemovalReason.DISCARDED));
		return orphans.size();
	}

	/** Keys of stations currently showing an archived (settled) thread. */
	Set<String> archived() {
		Set<String> out = new HashSet<>();
		stations.values().forEach(s -> {
			if (s.archived) {
				out.add(s.key);
			}
		});
		return out;
	}

	/** Removes every station entity from {@code lvl} (null = level already gone) and forgets them. */
	void clear(@Nullable ClientLevel lvl) {
		for (Station s : stations.values()) {
			discard(lvl, s);
		}
		stations.clear();
		byComputer.clear();
		byEntity.clear();
	}

	private Station spawn(ClientLevel lvl, String key, StationPlacement p) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
		d.setId(nextId--);
		d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		d.setLineWidth(LINE_WIDTH_PX);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.setTransformation(new Transformation(null, null, new Vector3f(p.scale()), null));
		d.snapTo(p.screenX(), p.screenY(), p.screenZ(), p.screenYaw(), 0f);
		lvl.addEntity(d);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		Station s = new Station(key, p, d);
		byComputer.put(p.computerKey(), key);
		byEntity.put(d.getId(), key);
		return s;
	}

	/** Prop + hitbox only while the computer block is air; kind per {@link StationProp}. */
	private void syncProp(ClientLevel lvl, Station s) {
		StationPlacement p = s.placement;
		boolean air = lvl.getBlockState(new BlockPos(p.computerX(), p.computerY(), p.computerZ())).isAir();
		StationProp want = StationProp.choose(T3Blocks.active(), !air);
		if (want == s.propKind && (s.prop != null) == want.drawn()) {
			return;
		}
		removeProp(lvl, s);
		s.propKind = want;
		if (!want.drawn()) {
			return;
		}
		Display.BlockDisplay prop = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, lvl);
		prop.setId(nextId--);
		prop.setBlockState(propState(want, Direction.fromYRot(p.screenYaw())));
		if (want == StationProp.PANEL) {
			// a low 0.8×0.4×0.8 base, centred in the cell, under the floating screen
			prop.setTransformation(new Transformation(new Vector3f(0.1f, 0f, 0.1f), null, new Vector3f(0.8f, 0.4f, 0.8f), null));
		}
		prop.snapTo(p.computerX(), p.computerY(), p.computerZ(), 0f, 0f);
		lvl.addEntity(prop);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		s.prop = prop;
		byEntity.put(prop.getId(), s.key);

		Interaction hit = new Interaction(EntityTypes.INTERACTION, lvl);
		hit.setId(nextId--);
		hit.setWidth(1.0f);
		hit.setHeight(HITBOX_HEIGHT);
		hit.snapTo(p.computerX() + 0.5, p.computerY(), p.computerZ() + 0.5, 0f, 0f);
		lvl.addEntity(hit);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		s.hitbox = hit;
		byEntity.put(hit.getId(), s.key);
	}

	private static BlockState propState(StationProp kind, Direction facing) {
		T3Blocks.Content c = T3Blocks.content();
		if (kind == StationProp.MONITOR && c != null) {
			return c.monitor(facing);
		}
		return Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
	}

	/**
	 * Older builds drew a vanilla observer as the stand-in computer. After a hot-swap those entities
	 * may still be in the level, untracked: discard every observer BlockDisplay in our id range.
	 */
	private static void sweepStaleProps(ClientLevel lvl) {
		List<Entity> stale = new ArrayList<>();
		for (Entity e : lvl.entitiesForRendering()) {
			if (e.getId() <= ID_BASE && e instanceof Display.BlockDisplay d && d.getBlockState().is(Blocks.OBSERVER)) {
				stale.add(e);
			}
		}
		stale.forEach(e -> remove(lvl, e));
	}

	private void removeProp(@Nullable ClientLevel lvl, Station s) {
		if (s.prop != null) {
			byEntity.remove(s.prop.getId());
			remove(lvl, s.prop);
			s.prop = null;
		}
		s.propKind = StationProp.NONE;
		if (s.hitbox != null) {
			byEntity.remove(s.hitbox.getId());
			remove(lvl, s.hitbox);
			s.hitbox = null;
		}
	}

	private void discard(@Nullable ClientLevel lvl, @Nullable Station s) {
		if (s == null) {
			return;
		}
		removeProp(lvl, s);
		byEntity.remove(s.screen.getId());
		remove(lvl, s.screen);
		byComputer.remove(s.placement.computerKey(), s.key);
	}

	private static void remove(@Nullable ClientLevel lvl, Entity e) {
		if (lvl != null && !e.isRemoved()) {
			lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	private static void apply(Display.TextDisplay d, StationScreen screen) {
		d.setText(component(screen));
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SET_TEXT);
		d.setBackgroundColor(screen.background());
	}

	static Component component(StationScreen screen) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (StationScreen.Line l : screen.lines()) {
			if (!first) {
				out.append("\n");
			}
			out.append(Component.literal(l.text()).withColor(l.color()));
			first = false;
		}
		return out;
	}
}
