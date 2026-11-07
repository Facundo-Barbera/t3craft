package dev.t3craft.client.workstation;

import com.mojang.math.Transformation;
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
import net.minecraft.world.level.block.ObserverBlock;
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
 *       {@link Display.BlockDisplay} "computer" prop (a vanilla observer, face towards the desk) and
 *       an {@link Interaction} hitbox so the player still has something to right-click. Over a real
 *       block neither exists and the click goes through {@code UseBlockCallback}.</li>
 * </ul>
 *
 * <p><b>Entity ids</b> count down from {@link #ID_BASE} = −5,000,000 (NpcRegistry uses −1,000,000…,
 * builders −2,000,000…, BehaviorDirector −3,000,000…). {@code NpcRegistry}'s orphan sweep treats
 * every id ≤ −1,000,000 as its own and may discard ours after one of its rare recoveries: each tick
 * checks {@link Entity#isRemoved()} and rebuilds a station whose entities vanished. Entities are kept
 * by reference and never looked up by id with a cast (hot-swap safe).
 *
 * <p>Entities are created once per station and only re-created when the placement moves or they
 * were removed; text is pushed only when {@link WorkstationCache} says the screen changed.
 */
final class WorkstationRenderer {
	static final int ID_BASE = -5_000_000;
	/** Scale of the screen text: 28 characters ≈ 1.9 blocks wide. */
	private static final float SCREEN_SCALE = 0.45f;
	private static final int LINE_WIDTH_PX = 170;
	private static final float HITBOX_HEIGHT = 1.75f;
	/** Re-check whether the computer block is air this often (ticks). */
	private static final int BLOCK_CHECK_TICKS = 20;

	private static final class Station {
		final String key;
		final StationPlacement placement;
		final Display.TextDisplay screen;
		Display.@Nullable BlockDisplay prop;
		@Nullable Interaction hitbox;
		boolean archived;
		/** The computer block draws its own screen (e.g. a t3craft monitor): our TextDisplay stays blank. */
		boolean suppressed;

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
	/** Computer blocks that draw their own screen; default none (vanilla blocks never do). */
	private Predicate<BlockState> selfDisplay = state -> false;

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
		source.prepare(threads);
		Set<String> alive = new HashSet<>();
		boolean checkBlocks = tick % BLOCK_CHECK_TICKS == 0;
		for (WorkstationThread t : threads) {
			StationPlacement p = source.placement(t);
			if (p == null) {
				continue;
			}
			alive.add(t.key());
			Station s = stations.get(t.key());
			boolean created = false;
			if (s == null || !s.placement.equals(p) || s.broken()) {
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
				boolean suppress = selfDisplay.test(lvl.getBlockState(new BlockPos(p.computerX(), p.computerY(), p.computerZ())));
				if (suppress != s.suppressed) {
					s.suppressed = suppress;
					if (suppress) {
						s.screen.setText(Component.empty());
						s.screen.setBackgroundColor(0);
					} else if (screen == null) {
						screen = cache.screen(t.key());
					}
				}
			}
			if (screen != null && !s.suppressed) {
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
		d.setTransformation(new Transformation(null, null, new Vector3f(SCREEN_SCALE), null));
		d.snapTo(p.screenX(), p.screenY(), p.screenZ(), p.screenYaw(), 0f);
		lvl.addEntity(d);
		Station s = new Station(key, p, d);
		byComputer.put(p.computerKey(), key);
		byEntity.put(d.getId(), key);
		return s;
	}

	/** Prop + hitbox only while the computer block is air. */
	private void syncProp(ClientLevel lvl, Station s) {
		StationPlacement p = s.placement;
		boolean air = lvl.getBlockState(new BlockPos(p.computerX(), p.computerY(), p.computerZ())).isAir();
		if (air && s.prop == null) {
			Display.BlockDisplay prop = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, lvl);
			prop.setId(nextId--);
			prop.setBlockState(Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.fromYRot(p.screenYaw())));
			prop.snapTo(p.computerX(), p.computerY(), p.computerZ(), 0f, 0f);
			lvl.addEntity(prop);
			s.prop = prop;
			byEntity.put(prop.getId(), s.key);

			Interaction hit = new Interaction(EntityTypes.INTERACTION, lvl);
			hit.setId(nextId--);
			hit.setWidth(1.0f);
			hit.setHeight(HITBOX_HEIGHT);
			hit.snapTo(p.computerX() + 0.5, p.computerY(), p.computerZ() + 0.5, 0f, 0f);
			lvl.addEntity(hit);
			s.hitbox = hit;
			byEntity.put(hit.getId(), s.key);
		} else if (!air && s.prop != null) {
			removeProp(lvl, s);
		}
	}

	private void removeProp(@Nullable ClientLevel lvl, Station s) {
		if (s.prop != null) {
			byEntity.remove(s.prop.getId());
			remove(lvl, s.prop);
			s.prop = null;
		}
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
