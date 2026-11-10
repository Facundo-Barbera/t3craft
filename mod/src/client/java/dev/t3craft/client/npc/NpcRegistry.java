package dev.t3craft.client.npc;

import dev.t3craft.T3Craft;
import dev.t3craft.client.ClientEntityIds;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.BehaviorDirector;
import dev.t3craft.client.npc.behavior.GridLayout;
import dev.t3craft.client.npc.behavior.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Client-only NPCs: per thread a {@link ThreadAvatar} (player model + skin + name tag) and a
 * floating {@link Display.TextDisplay} with environment, project and activity; per
 * neighbourhood (environment + project) a sign. All entities are added straight into the
 * {@link ClientLevel} with negative ids and are never known to any server. Runs on the client
 * thread.
 *
 * <p>Robustness: {@link #tick} never throws. Map entries are type-checked every tick (after a
 * dev hot-swap the live instance can still hold objects from older code, e.g. villagers), bad ones
 * are purged with their entities, and any unexpected error resets the registry: every entity in
 * our id range is removed from the level and the NPCs are rebuilt on the next tick.
 *
 * <p>fix/hq-gate-npcs: NPCs exist only while {@link dev.t3craft.client.hq.HqPresence#allows} is
 * open (own world, HQ standing, its dimension). Shut gate = every NPC and panel is removed; no
 * ring around the player, no group signs. They spawn at their HQ desk (else the HQ's way in) and
 * the behaviour director walks them from there. The {@link GridLayout} is kept only to fill an
 * anchor the HQ leaves empty, laid out from the HQ's way in. Particles over the head by state.
 * Avatars turn their head to a nearby player.
 */
public final class NpcRegistry {
	private static final int PARTICLE_EVERY_TICKS = 15;
	/** Client-only ids count down from here, far away from server-assigned (positive) ids. */
	static final int ID_BASE = -1_000_000;
	private static int nextClientId = ID_BASE;
	private static final long ERROR_LOG_INTERVAL_MS = 30_000;
	/** Re-apply the station policy this often, so done threads release their station on time (ticks). */
	private static final int REGATE_TICKS = 20 * 10;
	/** Sweep orphaned entities of our id range this often (ticks). */
	private static final int SWEEP_TICKS = 20 * 10;

	/** What an NPC's name tag and panel are built from (all immutable records). */
	private record LabelKey(NpcInfo info, LabelConfig labels, ChipsConfig chips, boolean compact, dev.t3craft.client.chat.ChatTheme theme) {
		/** World text pill plates (hologram style, compact mode). */
		boolean pills() {
			return compact && labels.hologramStyle();
		}
	}

	/** Built labels of one NPC, tagged with their key and the {@link #labelGen} of the batch. */
	private record Labels(String id, LabelKey key, int gen, Component nameTag, Component body, @Nullable Component pill) {
		static Labels of(String id, LabelKey k, int gen) {
			Component tag = k.labels().compact() ? Nameplate.compact(k.info(), k.labels()) : Nameplate.of(k.info(), k.chips());
			if (k.pills()) {
				// world text: the pill is the plate, the detail lines open under it
				Component body = Nameplate.pillDetail(k.info(), k.chips(), k.theme(), k.labels().titleMax());
				return new Labels(id, k, gen, tag, body, Nameplate.pill(k.info(), k.theme()));
			}
			Component body = k.compact() ? Nameplate.detail(k.info(), k.chips()) : Nameplate.info(k.info(), k.chips());
			return new Labels(id, k, gen, tag, body, null);
		}

		/** What the declutter pass measures as this NPC's plate: the pill, else the vanilla tag. */
		Component plate() {
			return pill != null ? pill : nameTag;
		}
	}

	private record Spawned(ThreadAvatar avatar, Display.TextDisplay panel) {
	}

	private final Map<String, NpcInfo> wanted = new LinkedHashMap<>();
	private final Map<String, Spawned> spawned = new LinkedHashMap<>();
	private final Map<String, Display.TextDisplay> signs = new LinkedHashMap<>();
	private final Map<Integer, String> npcByEntityId = new HashMap<>();
	/** Near-panel text per NPC (compact mode shows it only when {@link NpcLabels.Visibility} says so). */
	private final Map<String, Component> panelText = new HashMap<>();
	private final Map<String, NpcLabels.Visibility> detail = new HashMap<>();
	/** World text: NPCs whose name tag / detail panel show (decided once per tick by {@link #decide}). */
	private Set<String> tagsShown = Set.of();
	private Set<String> panelsShown = Set.of();
	/** Measured once per label text: name tag width, panel width and lines (code points / lines). */
	private final Map<String, Integer> tagCols = new HashMap<>();
	private final Map<String, Integer> panelCols = new HashMap<>();
	private final Map<String, Integer> panelLines = new HashMap<>();
	private PlateLayout.@Nullable Cache plateCache;
	/** Hologram style: the pill per NPC and the pill with its detail lines under it (built when either changes). */
	private final Map<String, Component> pillText = new HashMap<>();
	private final Map<String, Component> pillOpen = new HashMap<>();
	/** Pills mode and theme the labels were last built for (a change relabels everyone). */
	private boolean pillsMode = LabelConfig.DEFAULTS.pills();
	private dev.t3craft.client.chat.@Nullable ChatTheme labelTheme;
	/** Bottom of the pill above the feet: where the panel display stands (BehaviorDirector's PANEL_Y). */
	static final double PILL_BASE = 2.45;
	/** Height of the name tag's centre line above the feet (blocks). */
	static final double PLATE_BASE = 2.05;
	/**
	 * fix/hq-load-npc-reconcile: what each NPC's labels were built (or are being built) from; an NPC whose
	 * key did not change is not re-labelled. Client thread.
	 */
	private final Map<String, LabelKey> labelKey = new HashMap<>();
	/** The panel body last applied per NPC (before {@code panelExtra}). */
	private final Map<String, Component> panelBody = new HashMap<>();
	/** Label batches on the {@link dev.t3craft.build.Offload} workers, applied in the tick they are done. */
	private final List<java.util.concurrent.CompletableFuture<List<Labels>>> labelJobs = new ArrayList<>();
	/** Bumped when the labels are dropped (world change, clear): results of older batches are ignored. */
	private int labelGen;
	private boolean compactLabels = LabelConfig.DEFAULTS.compact();
	/** Walking, desks, board, bubbles (feat/npc-behavior). Owns its own entities; reset with ours. */
	private final BehaviorDirector behavior = new BehaviorDirector();
	/**
	 * Provisional desks when there is no HQ (fix/npc-grid-layout). Kept across reconciles. Lazy
	 * (read via {@link #fallbackLayout()}) so a hot-swap that adds it never sees null.
	 */
	private @Nullable GridLayout grid;
	/** Extra panel line per NPC id (feat/npc-helpers: "⚒ 2 helpers +3"); null = none. */
	private java.util.function.Function<String, @Nullable Component> panelExtra = id -> null;
	private @Nullable ClientLevel level;
	private @Nullable Vec3 anchor;
	private boolean dirty;
	/** fix/hq-gate-npcs: {@link dev.t3craft.client.hq.HqPresence#allows} as of the last tick. */
	private boolean hqOpen;
	/** {@link NpcGate#version()} last reconciled with (fix/hq-build-parallel). */
	private int gateSeen = -1;
	private int tick;
	private long lastErrorLogMs;
	/**
	 * fix/hq-stale-stations: what the sources send, gated by {@link dev.t3craft.hq.StationPolicy};
	 * {@link #wanted} holds only the stationed threads. Lazy ({@link #stations()}): null after a hot-swap.
	 */
	private @Nullable StationRoster stations;

	/** The station gate (policy, released threads). */
	public StationRoster stations() {
		StationRoster s = stations;
		if (s == null) {
			stations = s = new StationRoster();
		}
		return s;
	}

	public void replaceAll(List<NpcInfo> npcs) {
		replaceAll(npcs, null);
	}

	/**
	 * Every NPC the source shows. {@code lastActivity}: NPC id → last message/run (epoch ms); null or
	 * missing = unknown (inactivity of done threads then counts from when they were first seen done).
	 */
	public void replaceAll(List<NpcInfo> npcs, @Nullable Map<String, Long> lastActivity) {
		stations().replaceAll(npcs, lastActivity);
		regate();
	}

	public void upsert(NpcInfo npc) {
		stations().upsert(npc);
		regate();
	}

	public void remove(String id) {
		if (stations().remove(id)) {
			regate();
		}
	}

	/** Threads that released their station (settled, archived, done and inactive): for the HQ archive only, never spawned. */
	public List<NpcInfo> released() {
		return stations().released();
	}

	private void regate() {
		stations().regate(System.currentTimeMillis());
		wanted.clear();
		for (NpcInfo n : stations().stationed()) {
			wanted.put(n.id(), n);
		}
		dirty = true;
	}

	public @Nullable NpcInfo info(String npcId) {
		return wanted.get(npcId);
	}

	/**
	 * Every thread the source shows, with or without a station (chat, search, operate, status bar).
	 * World readers use {@link #stationed()} / {@link #hqRoster()} instead.
	 */
	public List<NpcInfo> all() {
		return stations().all();
	}

	/** Threads that hold a station: they get an NPC, a nametag, a workstation and a desk (fix/hq-stale-stations). */
	public List<NpcInfo> stationed() {
		return List.copyOf(wanted.values());
	}

	/** The HQ roster: {@link #stationed()} (desks) plus {@link #released()} (settled: archive only). */
	public List<NpcInfo> hqRoster() {
		List<NpcInfo> out = new ArrayList<>(wanted.values());
		out.addAll(released());
		return out;
	}

	/** NPC id for one of our client-only entities (avatar or its panel), or null if not ours. */
	public @Nullable String threadFor(Entity entity) {
		return npcByEntityId.get(entity.getId());
	}

	/** Where an NPC stands, if spawned. */
	public @Nullable Vec3 position(String npcId) {
		Spawned s = spawned.get(npcId);
		return s == null ? null : s.avatar().position();
	}

	public int size() {
		return wanted.size();
	}

	/** The avatar of a spawned NPC (feat/npc-helpers follows it), or null. */
	public net.minecraft.world.entity.@Nullable LivingEntity avatar(String npcId) {
		Spawned s = spawned.get(npcId);
		return s == null ? null : s.avatar();
	}

	/**
	 * Hook for a line appended to each NPC's panel (feat/npc-helpers). Applied when the panel text
	 * is refreshed, i.e. on the reconcile after the next {@link #replaceAll}/{@link #upsert}.
	 */
	public void setPanelExtra(java.util.function.@Nullable Function<String, @Nullable Component> extra) {
		this.panelExtra = extra == null ? id -> null : extra;
	}

	/** Left-click on an NPC: toggle its detail panel (compact labels). */
	public void toggleDetail(String npcId) {
		detail.computeIfAbsent(npcId, k -> new NpcLabels.Visibility()).toggle();
	}

	/** Open an NPC's detail panel for a while (e.g. when its chat is opened). */
	public void revealDetail(String npcId) {
		detail.computeIfAbsent(npcId, k -> new NpcLabels.Visibility()).reveal();
	}

	/**
	 * Origin of the fallback layout (the HQ's way in, fix/hq-gate-npcs), or null while no NPC may
	 * stand in this world. Read by feat/workstation to put the provisional
	 * computers next to the same ring desks the behaviour director uses.
	 */
	public @Nullable Vec3 layoutOrigin() {
		return anchor;
	}

	/**
	 * The provisional no-HQ layout (desks, homes, board) the behaviour director falls back to. Read
	 * by feat/workstation so the provisional computers stand in front of the same desks.
	 */
	public GridLayout fallbackLayout() {
		return grid = dev.t3craft.debug.HotSwapFields.orInit(grid, GridLayout::new);
	}

	/** NPC behaviour: plug a physical base in with {@code behavior().setAnchorSource(...)}. */
	public BehaviorDirector behavior() {
		return behavior;
	}

	/** Never throws: errors are logged (rate-limited) and the registry rebuilds itself. */
	public void tick(Minecraft mc) {
		try {
			validate(mc.level);
			tickUnsafe(mc);
		} catch (VirtualMachineError e) {
			if (!(e instanceof StackOverflowError)) {
				throw e;
			}
			recover(mc, e);
		} catch (Throwable e) {
			recover(mc, e);
		}
	}

	/** True for entity ids in the registry's own client-only range ({@link ClientEntityIds#NPC}). */
	static boolean isOurs(int entityId) {
		return ClientEntityIds.NPC.contains(entityId);
	}

	/**
	 * Drops map entries whose type is not what this code expects (stale objects after a hot-swap,
	 * removed entities) and discards their entities. Raw views on purpose: the generic types cannot
	 * be trusted in that situation.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void validate(@Nullable ClientLevel lvl) {
		boolean purged = false;
		// fix/npc-position-reset: a bad entry costs only its own NPC. Resetting the whole behaviour
		// director here put every NPC back on its spot (fresh brains snap) whenever one entry was off.
		boolean corrupt = false;
		List<String> lost = new ArrayList<>();
		for (java.util.Iterator<Map.Entry> it = ((Map) spawned).entrySet().iterator(); it.hasNext(); ) {
			Map.Entry e = it.next();
			Object v = e.getValue();
			if (e.getKey() instanceof String && v instanceof Spawned s && s.avatar() != null && s.panel() != null
					&& !s.avatar().isRemoved() && !s.panel().isRemoved()) {
				continue;
			}
			if (e.getKey() instanceof String id) {
				lost.add(id);
			} else {
				corrupt = true;
			}
			if (v instanceof Spawned s) {
				if (s.avatar() != null) {
					npcByEntityId.remove(s.avatar().getId());
				}
				if (s.panel() != null) {
					npcByEntityId.remove(s.panel().getId());
				}
			}
			discard(lvl, v);
			it.remove();
			purged = true;
		}
		for (java.util.Iterator<Map.Entry> it = ((Map) signs).entrySet().iterator(); it.hasNext(); ) {
			Map.Entry e = it.next();
			if (e.getKey() instanceof String && e.getValue() instanceof Display.TextDisplay d && !d.isRemoved()) {
				continue;
			}
			discard(lvl, e.getValue());
			it.remove();
			purged = true;
		}
		for (java.util.Iterator<Map.Entry> it = ((Map) npcByEntityId).entrySet().iterator(); it.hasNext(); ) {
			Map.Entry e = it.next();
			if (!(e.getKey() instanceof Integer) || !(e.getValue() instanceof String)) {
				it.remove();
				purged = true;
				corrupt = true;
			}
		}
		for (java.util.Iterator<Map.Entry> it = ((Map) wanted).entrySet().iterator(); it.hasNext(); ) {
			Map.Entry e = it.next();
			if (!(e.getKey() instanceof String) || !(e.getValue() instanceof NpcInfo)) {
				it.remove();
				purged = true;
				corrupt = true;
			}
		}
		if (purged) {
			if (corrupt) {
				// Stale objects from older code (hot-swap): nothing can be trusted, rebuild everything.
				// Behaviour overlays share our id range; drop them first so the sweep does not orphan them.
				behavior.reset(lvl);
			} else {
				// Only the lost NPCs respawn (with a fresh brain); the others keep brain, route and state.
				lost.forEach(id -> behavior.forget(id, lvl));
			}
			sweep(lvl);
			dirty = true;
		}
	}

	/**
	 * fix/npc-position-reset: what one reconcile does. Only {@code added} get a new entity and brain,
	 * only {@code removed} lose theirs; {@code kept} keep entity, brain, route and overlays untouched.
	 */
	record RosterDiff(List<String> added, List<String> removed, List<String> kept) {
		static RosterDiff of(java.util.Collection<String> spawnedIds, java.util.Collection<String> wantedIds) {
			java.util.Set<String> want = new java.util.LinkedHashSet<>(wantedIds);
			java.util.Set<String> have = new java.util.LinkedHashSet<>(spawnedIds);
			List<String> added = new ArrayList<>();
			List<String> kept = new ArrayList<>();
			for (String id : want) {
				(have.contains(id) ? kept : added).add(id);
			}
			List<String> removed = new ArrayList<>();
			for (String id : have) {
				if (!want.contains(id)) {
					removed.add(id);
				}
			}
			return new RosterDiff(List.copyOf(added), List.copyOf(removed), List.copyOf(kept));
		}
	}

	private static void discard(@Nullable ClientLevel lvl, @Nullable Object v) {
		if (lvl == null || v == null) {
			return;
		}
		if (v instanceof Entity ent) {
			lvl.removeEntity(ent.getId(), Entity.RemovalReason.DISCARDED);
		} else if (v instanceof Spawned s) {
			if (s.avatar() != null) {
				lvl.removeEntity(s.avatar().getId(), Entity.RemovalReason.DISCARDED);
			}
			if (s.panel() != null) {
				lvl.removeEntity(s.panel().getId(), Entity.RemovalReason.DISCARDED);
			}
		}
	}

	/** Removes every entity in our id range that the current maps do not track (orphans). */
	private void sweep(@Nullable ClientLevel lvl) {
		if (lvl == null) {
			return;
		}
		java.util.Set<Integer> tracked = new java.util.HashSet<>(npcByEntityId.keySet());
		signs.values().forEach(d -> tracked.add(d.getId()));
		List<Integer> present = new ArrayList<>();
		for (Entity e : lvl.entitiesForRendering()) {
			present.add(e.getId());
		}
		List<Integer> orphans = ClientEntityIds.NPC.orphans(present, tracked);
		orphans.forEach(id -> lvl.removeEntity(id, Entity.RemovalReason.DISCARDED));
	}

	private void recover(Minecraft mc, Throwable error) {
		long now = System.currentTimeMillis();
		if (now - lastErrorLogMs > ERROR_LOG_INTERVAL_MS) {
			lastErrorLogMs = now;
			T3Craft.LOGGER.warn("T3Craft NPC tick failed; rebuilding NPCs", error);
		}
		try {
			ClientLevel lvl = mc.level;
			spawned.clear();
			signs.clear();
			npcByEntityId.clear();
			panelText.clear();
			dropLabels();
			behavior.reset(lvl);
			if (lvl != null) {
				List<Integer> ours = new ArrayList<>();
				for (Entity e : lvl.entitiesForRendering()) {
					// Only the registry's and the behaviour overlays' ranges: never builders, helpers or workstations.
					if (isOurs(e.getId()) || ClientEntityIds.BEHAVIOR.contains(e.getId())) {
						ours.add(e.getId());
					}
				}
				ours.forEach(id -> lvl.removeEntity(id, Entity.RemovalReason.DISCARDED));
			}
		} catch (Throwable ignored) {
			// best effort; next tick starts from clean maps anyway
		}
		dirty = true;
	}

	private void tickUnsafe(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			// World changed or left: old entities died with the old level.
			spawned.clear();
			signs.clear();
			npcByEntityId.clear();
			panelText.clear();
			dropLabels();
			detail.clear();
			behavior.reset(null);
			level = lvl;
			anchor = null;
			fallbackLayout().clear();
			dirty = true;
		}
		if (lvl == null || mc.player == null) {
			return;
		}
		tick++;
		if (tick % REGATE_TICKS == 0 && stations().regate(System.currentTimeMillis())) {
			// a done thread went past the idle threshold: it releases its station now
			wanted.clear();
			stations().stationed().forEach(n -> wanted.put(n.id(), n));
			dirty = true;
		}
		if (tick % SWEEP_TICKS == 0) {
			sweep(lvl); // NPCs, nametags and signs of threads that no longer hold a station
		}
		if (gateSeen != NpcGate.version()) {
			gateSeen = NpcGate.version();
			dirty = true;
		}
		// fix/hq-gate-npcs: the one gate. Shut (no HQ here, remote server, other dimension, HQ reset or
		// rebuilt from scratch): every NPC goes; open: they come back at their HQ desks.
		boolean open = dev.t3craft.client.hq.HqPresence.allows(mc);
		if (open != hqOpen) {
			hqOpen = open;
			anchor = null;
			fallbackLayout().clear();
			dirty = true;
		}
		if (dirty) {
			dirty = false;
			if (!hqOpen) {
				if (!spawned.isEmpty() || !signs.isEmpty()) {
					reconcile(lvl, mc.player.position()); // shown is empty: removes everyone
				}
			} else {
				if (anchor == null && !wanted.isEmpty()) {
					anchor = hqOrigin(mc);
				}
				if (anchor != null) {
					reconcile(lvl, anchor);
				}
			}
		}
		applyLabels(); // fix/hq-load-npc-reconcile: labels built off-thread since the last tick
		updatePanels(mc);
		List<BehaviorDirector.Body> bodies = new ArrayList<>(spawned.size());
		for (Map.Entry<String, Spawned> e : spawned.entrySet()) {
			ThreadAvatar a = e.getValue().avatar();
			NpcInfo info = wanted.get(e.getKey());
			if (info != null) {
				bodies.add(new BehaviorDirector.Body(e.getKey(), info, a, e.getValue().panel()));
			}
			if (info != null && (tick + Math.abs(a.getId())) % PARTICLE_EVERY_TICKS == 0) {
				SimpleParticleType p = particle(info.particle());
				if (p != null) {
					lvl.addParticle(p, a.getX() + (a.getRandom().nextDouble() - 0.5) * 0.6, a.getY() + 2.25, a.getZ() + (a.getRandom().nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
				}
			}
		}
		if (hqOpen && anchor != null) {
			behavior.tick(lvl, mc.player, fallbackLayout(), bodies);
		}
	}

	/** Where the fallback grid starts: the HQ's way in, else its hub exit, else the player (HQ world only). */
	private Vec3 hqOrigin(Minecraft mc) {
		Anchor in = behavior.anchorSource().spawn();
		if (in == null) {
			in = behavior.anchorSource().exit();
		}
		return in != null ? new Vec3(in.pos().x(), in.pos().y(), in.pos().z()) : mc.player.position();
	}

	public void clear(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != null) {
			spawned.values().forEach(s -> {
				lvl.removeEntity(s.avatar().getId(), Entity.RemovalReason.DISCARDED);
				lvl.removeEntity(s.panel().getId(), Entity.RemovalReason.DISCARDED);
			});
			signs.values().forEach(s -> lvl.removeEntity(s.getId(), Entity.RemovalReason.DISCARDED));
		}
		behavior.reset(lvl);
		spawned.clear();
		signs.clear();
		npcByEntityId.clear();
		panelText.clear();
		dropLabels();
		detail.clear();
		wanted.clear();
		stations().clear();
		fallbackLayout().clear();
	}

	static @Nullable SimpleParticleType particle(@Nullable String key) {
		if (key == null) {
			return null;
		}
		return switch (key) {
			case "working" -> ParticleTypes.ENCHANT;
			case "attention" -> ParticleTypes.NOTE;
			case "error" -> ParticleTypes.ANGRY_VILLAGER;
			case "done" -> ParticleTypes.HAPPY_VILLAGER;
			default -> null;
		};
	}

	/**
	 * fix/hq-build-stutter, fix/hq-load-npc-reconcile: NPCs (avatar + panel) spawned per client tick when
	 * the HQ gate opens or a wing brings many at once; the rest come on the next ticks (a {@code dirty}
	 * reconcile each).
	 */
	static final int SPAWNS_PER_TICK = 2;
	/** fix/hq-load-npc-reconcile: NPCs removed per client tick (two entities each, plus overlays). */
	static final int REMOVALS_PER_TICK = 4;
	/** fix/hq-load-npc-reconcile: once a reconcile has spent this long, further spawns and removals wait a tick. */
	static final long RECONCILE_BUDGET_NS = 4_000_000L;
	/** Changed labels built inline below this count; more go to a worker. */
	static final int INLINE_LABELS = 2;

	private void reconcile(ClientLevel lvl, Vec3 a) {
		long t0 = System.nanoTime();
		try {
			reconcileRoster(lvl, a, t0);
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_NPC_RECONCILE, t0);
		}
	}

	private static boolean overBudget(long t0, int doneThisTick) {
		return doneThisTick > 0 && System.nanoTime() - t0 >= RECONCILE_BUDGET_NS;
	}

	private void reconcileRoster(ClientLevel lvl, Vec3 a, long t0) {
		// Incremental (fix/npc-position-reset): only NPCs no longer wanted go, only new ones spawn below;
		// the others keep their entity (where it stands), brain and overlays.
		// fix/hq-build-parallel: nobody waits around a half-built HQ (NpcGate).
		// fix/hq-gate-npcs: nobody at all while the HQ gate is shut.
		// fix/hq-load-npc-reconcile: timed in parts (plan, remove, spawn, labels), spawns and removals
		// spread over ticks (SPAWNS_PER_TICK, REMOVALS_PER_TICK, RECONCILE_BUDGET_NS).
		long p0 = System.nanoTime();
		Map<String, NpcInfo> shown = hqOpen ? NpcGate.shown(wanted, spawned.keySet()) : Map.of();
		RosterDiff diff = RosterDiff.of(spawned.keySet(), shown.keySet());

		// Provisional desks: stable grid cells, one block of desks per neighbourhood.
		List<GridLayout.Member> members = new ArrayList<>(shown.size());
		for (NpcInfo n : shown.values()) {
			members.add(new GridLayout.Member(n.id(), n.groupId(), n.envLabel()));
		}
		fallbackLayout().update(new Pos(a.x, a.y, a.z), members, BehaviorDirector.ground(lvl, a.y));

		Map<String, List<NpcInfo>> groups = new LinkedHashMap<>();
		List<NpcInfo> ordered = new ArrayList<>(shown.values());
		ordered.sort(Comparator.comparingInt(NpcInfo::priority).thenComparing(NpcInfo::id));
		for (NpcInfo n : ordered) {
			groups.computeIfAbsent(n.groupId(), k -> new ArrayList<>()).add(n);
		}
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_NPC_PLAN, p0);

		boolean more = false;
		long r0 = System.nanoTime();
		int removedNow = 0;
		for (String id : diff.removed()) {
			if (removedNow >= REMOVALS_PER_TICK || overBudget(t0, removedNow)) {
				more = true; // the rest on the next ticks
				break;
			}
			removedNow++;
			Spawned s = spawned.remove(id);
			behavior.forget(id, lvl);
			npcByEntityId.remove(s.avatar().getId());
			npcByEntityId.remove(s.panel().getId());
			panelText.remove(id);
			forgetMeasures(id);
			panelBody.remove(id);
			labelKey.remove(id);
			detail.remove(id);
			lvl.removeEntity(s.avatar().getId(), Entity.RemovalReason.DISCARDED);
			lvl.removeEntity(s.panel().getId(), Entity.RemovalReason.DISCARDED);
		}
		// fix/hq-gate-npcs: no neighbourhood signs any more (they floated over the provisional grid);
		// the HQ has its own signage. Any left from older code go.
		for (String gid : new ArrayList<>(signs.keySet())) {
			lvl.removeEntity(signs.remove(gid).getId(), Entity.RemovalReason.DISCARDED);
		}
		if (removedNow > 0) {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_NPC_REMOVE, r0);
		}

		long s0 = System.nanoTime();
		long labelNs = 0;
		int spawnedNow = 0;
		LabelConfig labelCfg = LabelConfig.current();
		ChipsConfig chips = ChipsConfig.current();
		dev.t3craft.client.chat.ChatTheme theme = WorldText.theme();
		labelTheme = theme;
		List<Map.Entry<String, LabelKey>> relabel = new ArrayList<>();
		for (Map.Entry<String, List<NpcInfo>> group : groups.entrySet()) {
			for (NpcInfo info : group.getValue()) {
				LabelKey key = new LabelKey(info, labelCfg, chips, compactLabels, theme);
				Spawned s = spawned.get(info.id());
				if (s == null || s.avatar().isRemoved()) {
					if (spawnedNow >= SPAWNS_PER_TICK || overBudget(t0, spawnedNow)) {
						more = true; // fix/hq-build-stutter: the next ones on the next tick
						continue;
					}
					// Spawn at the HQ desk (or the HQ's way in) so nobody walks in from the grass.
					Anchor desk = behavior.anchorSource().desk(info.id(), info.groupId());
					if (desk == null) {
						desk = behavior.anchorSource().spawn();
					}
					if (desk == null) {
						desk = fallbackLayout().desk(info.id(), info.groupId());
					}
					Vec3 pos = desk == null ? a : new Vec3(desk.pos().x(), desk.pos().y(), desk.pos().z());
					spawnedNow++;
					Labels l = Labels.of(info.id(), key, labelGen);
					s = spawn(lvl, info, l.nameTag(), pos, desk == null ? 0f : desk.yaw());
					spawned.put(info.id(), s);
					npcByEntityId.put(s.avatar().getId(), info.id());
					npcByEntityId.put(s.panel().getId(), info.id());
					labelKey.put(info.id(), key);
					tagCols.put(info.id(), cols(l.plate()));
					putPill(info.id(), l.pill());
					panelBody.put(info.id(), l.body());
					setPanelText(info.id());
					continue;
				}
				// Once spawned, the BehaviorDirector moves the avatar and its panel; labels only when they changed.
				long l0 = System.nanoTime();
				if (!key.equals(labelKey.get(info.id()))) {
					labelKey.put(info.id(), key); // pending: the batch result must still match it
					relabel.add(Map.entry(info.id(), key));
				} else {
					setPanelText(info.id()); // panelExtra may have changed
				}
				labelNs += System.nanoTime() - l0;
			}
		}
		if (spawnedNow > 0) {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_NPC_SPAWN, s0 + labelNs);
		}
		if (!relabel.isEmpty()) {
			long l0 = System.nanoTime();
			int gen = labelGen;
			if (relabel.size() <= INLINE_LABELS) {
				for (Map.Entry<String, LabelKey> e : relabel) {
					applyLabel(Labels.of(e.getKey(), e.getValue(), gen));
				}
			} else {
				List<Map.Entry<String, LabelKey>> batch = List.copyOf(relabel);
				labelJobs.add(dev.t3craft.build.Offload.supply(dev.t3craft.debug.PerfStats.STEP_NPC_LABELS_ASYNC, () -> {
					List<Labels> out = new ArrayList<>(batch.size());
					for (Map.Entry<String, LabelKey> e : batch) {
						out.add(Labels.of(e.getKey(), e.getValue(), gen));
					}
					return out;
				}));
			}
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_NPC_LABELS, l0);
		}
		updatePanels(null);
		if (more) {
			dirty = true;
		}
	}

	/** Panel text = the last built body plus {@code panelExtra}. Client thread. */
	private void setPanelText(String id) {
		Component body = panelBody.get(id);
		if (body == null) {
			return;
		}
		Component extra = panelExtra.apply(id);
		Component text = extra == null ? body : body.copy().append(extra);
		Component before = panelText.put(id, text);
		if (!text.equals(before)) {
			panelCols.put(id, cols(text));
			panelLines.put(id, NpcLabels.lines(text.getString()));
			composePill(id);
		}
	}

	/** Hologram style: the NPC's pill (null in classic style). */
	private void putPill(String id, @Nullable Component pill) {
		if (pill == null) {
			pillText.remove(id);
			pillOpen.remove(id);
			return;
		}
		if (!pill.equals(pillText.put(id, pill))) {
			composePill(id);
		}
	}

	/** Pill + detail lines, built once per change of either (so a steady tick pushes the same instance). */
	private void composePill(String id) {
		Component pill = pillText.get(id);
		Component detailText = panelText.get(id);
		if (pill == null) {
			pillOpen.remove(id);
			return;
		}
		pillOpen.put(id, detailText == null ? pill : pill.copy().append(Component.literal("\n")).append(detailText));
	}

	/** Applies labels built for the current NPC, if they are still the wanted ones (key and generation). */
	private void applyLabel(Labels l) {
		Spawned s = spawned.get(l.id());
		if (s == null || l.gen() != labelGen || !l.key().equals(labelKey.get(l.id()))) {
			return; // gone, dropped, or re-labelled again since
		}
		s.avatar().update(l.key().info(), l.nameTag());
		tagCols.put(l.id(), cols(l.plate()));
		putPill(l.id(), l.pill());
		panelBody.put(l.id(), l.body());
		setPanelText(l.id());
	}

	/** Takes in the label batches the workers finished. Client thread. */
	private void applyLabels() {
		if (labelJobs.isEmpty()) {
			return;
		}
		long t0 = System.nanoTime();
		for (java.util.Iterator<java.util.concurrent.CompletableFuture<List<Labels>>> it = labelJobs.iterator(); it.hasNext();) {
			java.util.concurrent.CompletableFuture<List<Labels>> f = it.next();
			if (!f.isDone()) {
				continue;
			}
			it.remove();
			List<Labels> done;
			try {
				done = f.join();
			} catch (RuntimeException e) {
				T3Craft.LOGGER.debug("NPC label batch failed; relabelling on the next reconcile", e);
				labelKey.clear(); // forces a fresh batch for everyone
				dirty = true;
				continue;
			}
			done.forEach(this::applyLabel);
		}
		dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_NPC_LABELS, t0);
	}

	private void forgetMeasures(String id) {
		pillText.remove(id);
		pillOpen.remove(id);
		tagCols.remove(id);
		panelCols.remove(id);
		panelLines.remove(id);
	}

	/** Forgets every label (world change, clear): batches still running are dropped by generation. */
	private void dropLabels() {
		pillText.clear();
		pillOpen.clear();
		tagCols.clear();
		panelCols.clear();
		panelLines.clear();
		tagsShown = Set.of();
		panelsShown = Set.of();
		if (plateCache != null) {
			plateCache.clear();
		}
		labelGen++;
		labelJobs.forEach(f -> f.cancel(false));
		labelJobs.clear();
		labelKey.clear();
		panelBody.clear();
	}

	/**
	 * World text (docs/features/world-text.md): which name tags and detail panels show, once per tick.
	 *
	 * <ul>
	 *   <li>Detailed mode: every tag and every panel (the old layout; only {@code hologram.npcTags} applies).</li>
	 *   <li>Compact mode: a panel wants to open as before ({@link NpcLabels.Visibility}: near, aimed or
	 *       clicked); only {@code maxPanels} of those open, clicked first, then aimed, then nearest. A
	 *       name tag shows within {@code nearDistance} or while aimed at (with hysteresis); at most
	 *       {@code maxVisible} plates show and none overlaps another ({@link PlateLayout}, recomputed
	 *       only when something moved noticeably). The open panel's NPC is always among them.</li>
	 * </ul>
	 * With {@code mc == null} (from reconcile) only re-applies the last decision.
	 */
	private void updatePanels(@Nullable Minecraft mc) {
		LabelConfig cfg = LabelConfig.current();
		if (cfg.compact() != compactLabels) {
			compactLabels = cfg.compact();
			dirty = true; // rebuild name tags and panel texts for the new mode
		}
		dev.t3craft.client.chat.ChatTheme theme = WorldText.theme();
		if (cfg.pills() != pillsMode || (labelTheme != null && theme != labelTheme)) {
			pillsMode = cfg.pills();
			labelTheme = theme;
			dirty = true; // pills on/off or a restyle (F3+T): rebuild every label
		}
		boolean on = cfg.sources().npcTags();
		if (mc != null && mc.player != null) {
			decide(mc, cfg, on);
		}
		boolean pills = pillsMode;
		int plateBg = ChatTheme.withAlpha(theme.panel(), WorldText.PLATE_ALPHA);
		for (Map.Entry<String, Spawned> e : spawned.entrySet()) {
			String id = e.getKey();
			Display.TextDisplay panel = e.getValue().panel();
			Component want;
			int bg;
			if (pills) {
				// world text: no vanilla tag; the panel display is the pill, the detail opens under it
				e.getValue().avatar().setTagHidden(true);
				Component pill = pillText.get(id);
				boolean plate = on && pill != null && tagsShown.contains(id);
				Component open = plate && panelsShown.contains(id) ? pillOpen.get(id) : null;
				want = !plate ? Component.empty() : open != null ? open : pill;
				bg = plate ? plateBg : 0;
			} else {
				Component text = panelText.get(id);
				boolean show = on && text != null && panelsShown.contains(id);
				e.getValue().avatar().setTagHidden(!on || !tagsShown.contains(id));
				want = show ? text : Component.empty();
				bg = show ? 0x60000000 : 0;
			}
			if (!Objects.equals(panel.getText(), want)) {
				panel.setText(want);
				dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SET_TEXT);
				panel.setBackgroundColor(bg);
			}
		}
	}

	/** Fills {@link #tagsShown} and {@link #panelsShown} for this tick. */
	private void decide(Minecraft mc, LabelConfig cfg, boolean on) {
		Set<String> tags = new HashSet<>();
		Set<String> panels = new HashSet<>();
		if (!on) {
			tagsShown = tags;
			panelsShown = panels;
			return;
		}
		if (!compactLabels) {
			for (String id : spawned.keySet()) {
				tags.add(id);
				if (panelText.get(id) != null) {
					panels.add(id);
				}
			}
			tagsShown = tags;
			panelsShown = panels;
			return;
		}
		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = mc.player.getViewVector(1f);
		Vec3 feet = mc.player.position();
		record Seen(String id, ThreadAvatar a, double dist, double aim, boolean wants, boolean pinned) {
		}
		List<Seen> seen = new ArrayList<>(spawned.size());
		for (Map.Entry<String, Spawned> e : spawned.entrySet()) {
			String id = e.getKey();
			ThreadAvatar a = e.getValue().avatar();
			double dist = a.position().distanceTo(feet);
			double aim = NpcLabels.aimMiss(eye.x, eye.y, eye.z, look.x, look.y, look.z, a.getX(), a.getY() + 1.2, a.getZ(), NpcLabels.AIM_RANGE);
			NpcLabels.Visibility v = detail.computeIfAbsent(id, k -> new NpcLabels.Visibility());
			boolean wants = panelText.get(id) != null && v.update(dist, aim, cfg.detailDistance());
			seen.add(new Seen(id, a, dist, aim, wants, v.pinned()));
		}
		// At most maxPanels detail panels: clicked first, then the aimed one, then the nearest.
		List<Seen> wanting = new ArrayList<>();
		for (Seen s : seen) {
			if (s.wants()) {
				wanting.add(s);
			}
		}
		wanting.sort(java.util.Comparator.comparing((Seen s) -> !s.pinned()).thenComparingDouble(Seen::aim).thenComparingDouble(Seen::dist)
				.thenComparing(Seen::id));
		for (Seen s : wanting) {
			if (cfg.maxPanels() > 0 && panels.size() >= cfg.maxPanels()) {
				break;
			}
			panels.add(s.id());
		}
		// Name tag candidates: near or aimed (hysteresis on both), plus every open panel's NPC.
		List<PlateLayout.Plate> plates = new ArrayList<>();
		for (Seen s : seen) {
			boolean was = tagsShown.contains(s.id());
			double near = cfg.nearDistance();
			boolean candidate = panels.contains(s.id()) || near <= 0 || s.dist() <= near + (was ? NpcLabels.HYSTERESIS : 0)
					|| s.aim() <= (was ? NpcLabels.AIM_KEEP : NpcLabels.AIM_SHOW);
			if (!candidate) {
				continue;
			}
			int lines = 1 + (panels.contains(s.id()) ? panelLines.getOrDefault(s.id(), 0) : 0);
			int cols = Math.max(tagCols.getOrDefault(s.id(), 8), panels.contains(s.id()) ? panelCols.getOrDefault(s.id(), 0) : 0);
			double h = PlateLayout.height(lines, 1f);
			ThreadAvatar a = s.a();
			// The tag sits at ≈ 2.1 above the feet (a pill at the panel's 2.45); an open panel grows upwards from there.
			double base = pillsMode ? PILL_BASE : PLATE_BASE;
			plates.add(new PlateLayout.Plate(s.id(), a.getX(), a.getY() + base + h / 2, a.getZ(), PlateLayout.width(cols, 1f), h,
					s.dist(), s.aim(), panels.contains(s.id())));
		}
		Set<String> visible = cfg.maxVisible() <= 0 && !cfg.declutter()
				? idsOf(plates)
				: declutter().update(plates, eye.x, eye.y, eye.z, cfg.maxVisible(), cfg.declutter());
		tags.addAll(visible);
		panels.retainAll(visible);
		tagsShown = tags;
		panelsShown = panels;
	}

	private static Set<String> idsOf(List<PlateLayout.Plate> plates) {
		Set<String> out = new HashSet<>();
		plates.forEach(p -> out.add(p.id()));
		return out;
	}

	/** Lazy (hot-swap safe). */
	private PlateLayout.Cache declutter() {
		PlateLayout.Cache c = plateCache;
		if (c == null) {
			plateCache = c = new PlateLayout.Cache();
		}
		return c;
	}

	/** Longest line (code points) and line count of a label, for the declutter pass; measured once per text. */
	static int cols(@Nullable Component c) {
		if (c == null) {
			return 0;
		}
		int best = 0;
		for (String line : c.getString().split("\n", -1)) {
			best = Math.max(best, line.codePointCount(0, line.length()));
		}
		return best;
	}

	private static Display.TextDisplay textDisplay(ClientLevel lvl, int background) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
		d.setId(nextClientId--);
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setBackgroundColor(background);
		d.setLineWidth(220);
		lvl.addEntity(d);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		return d;
	}

	private static Spawned spawn(ClientLevel lvl, NpcInfo info, Component nameTag, Vec3 pos, float yaw) {
		ThreadAvatar avatar = new ThreadAvatar(lvl, info.id());
		avatar.setId(nextClientId--);
		avatar.setNoGravity(true);
		avatar.setSilent(true);
		avatar.update(info, nameTag);
		avatar.snapTo(pos.x, pos.y, pos.z, yaw, 0f);
		lvl.addEntity(avatar);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		Display.TextDisplay panel = textDisplay(lvl, 0);
		panel.snapTo(pos.x, pos.y + 2.45, pos.z, 0f, 0f);
		T3Craft.LOGGER.debug("spawned client-only NPC {} at {}", info.id(), pos);
		return new Spawned(avatar, panel);
	}
}
