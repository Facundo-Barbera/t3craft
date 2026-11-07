package dev.t3craft.client.npc;

import dev.t3craft.T3Craft;
import dev.t3craft.client.npc.behavior.BehaviorDirector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * <p>Layout: neighbourhoods on a grid next to where the player stood when the first NPC appeared
 * in this level (most urgent neighbourhood first); NPCs on a ring inside each plot, ordered by
 * the resolved priority. Particles over the head by state. Avatars turn their head to a nearby
 * player.
 */
public final class NpcRegistry {
	private static final double PLOT = 12.0;
	private static final int PLOTS_PER_ROW = 3;
	private static final int PARTICLE_EVERY_TICKS = 15;
	/** Client-only ids count down from here, far away from server-assigned (positive) ids. */
	static final int ID_BASE = -1_000_000;
	private static int nextClientId = ID_BASE;
	private static final long ERROR_LOG_INTERVAL_MS = 30_000;

	private record Spawned(ThreadAvatar avatar, Display.TextDisplay panel) {
	}

	private final Map<String, NpcInfo> wanted = new LinkedHashMap<>();
	private final Map<String, Spawned> spawned = new LinkedHashMap<>();
	private final Map<String, Display.TextDisplay> signs = new LinkedHashMap<>();
	private final Map<Integer, String> npcByEntityId = new HashMap<>();
	/** Walking, desks, board, bubbles (feat/npc-behavior). Owns its own entities; reset with ours. */
	private final BehaviorDirector behavior = new BehaviorDirector();
	/** Extra panel line per NPC id (feat/npc-helpers: "⚒ 2 helpers +3"); null = none. */
	private java.util.function.Function<String, @Nullable Component> panelExtra = id -> null;
	private @Nullable ClientLevel level;
	private @Nullable Vec3 anchor;
	private boolean dirty;
	private int tick;
	private long lastErrorLogMs;

	public void replaceAll(List<NpcInfo> npcs) {
		wanted.clear();
		for (NpcInfo n : npcs) {
			wanted.put(n.id(), n);
		}
		dirty = true;
	}

	public void upsert(NpcInfo npc) {
		wanted.put(npc.id(), npc);
		dirty = true;
	}

	public void remove(String id) {
		if (wanted.remove(id) != null) {
			dirty = true;
		}
	}

	public @Nullable NpcInfo info(String npcId) {
		return wanted.get(npcId);
	}

	public List<NpcInfo> all() {
		return List.copyOf(wanted.values());
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

	/**
	 * Origin of the fallback layout (where the player stood when the first NPC appeared in this
	 * level, offset by 4,0,4), or null before that. Read by feat/workstation to put the provisional
	 * computers next to the same ring desks the behaviour director uses.
	 */
	public @Nullable Vec3 layoutOrigin() {
		return anchor;
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

	/** True for entity ids in T3Craft's client-only range. */
	static boolean isOurs(int entityId) {
		return entityId <= ID_BASE;
	}

	/**
	 * Drops map entries whose type is not what this code expects (stale objects after a hot-swap,
	 * removed entities) and discards their entities. Raw views on purpose: the generic types cannot
	 * be trusted in that situation.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void validate(@Nullable ClientLevel lvl) {
		boolean purged = false;
		for (java.util.Iterator<Map.Entry> it = ((Map) spawned).entrySet().iterator(); it.hasNext(); ) {
			Map.Entry e = it.next();
			Object v = e.getValue();
			if (e.getKey() instanceof String && v instanceof Spawned s && s.avatar() != null && s.panel() != null
					&& !s.avatar().isRemoved() && !s.panel().isRemoved()) {
				continue;
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
			}
		}
		for (java.util.Iterator<Map.Entry> it = ((Map) wanted).entrySet().iterator(); it.hasNext(); ) {
			Map.Entry e = it.next();
			if (!(e.getKey() instanceof String) || !(e.getValue() instanceof NpcInfo)) {
				it.remove();
				purged = true;
			}
		}
		if (purged) {
			// Behaviour overlays share our id range; drop them first so the sweep does not orphan them.
			behavior.reset(lvl);
			sweep(lvl);
			dirty = true;
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
		List<Integer> orphans = new ArrayList<>();
		for (Entity e : lvl.entitiesForRendering()) {
			if (isOurs(e.getId()) && !tracked.contains(e.getId())) {
				orphans.add(e.getId());
			}
		}
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
			behavior.reset(lvl);
			if (lvl != null) {
				List<Integer> ours = new ArrayList<>();
				for (Entity e : lvl.entitiesForRendering()) {
					if (isOurs(e.getId())) {
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
			behavior.reset(null);
			level = lvl;
			anchor = null;
			dirty = true;
		}
		if (lvl == null || mc.player == null) {
			return;
		}
		if (dirty) {
			dirty = false;
			if (anchor == null && !wanted.isEmpty()) {
				anchor = mc.player.position().add(4, 0, 4);
			}
			if (anchor != null) {
				reconcile(lvl, anchor);
			}
		}
		tick++;
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
		if (anchor != null) {
			behavior.tick(lvl, mc.player, anchor, bodies);
		}
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
		wanted.clear();
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

	private void reconcile(ClientLevel lvl, Vec3 a) {
		// Remove NPCs no longer wanted.
		for (String id : new ArrayList<>(spawned.keySet())) {
			if (!wanted.containsKey(id)) {
				Spawned s = spawned.remove(id);
				behavior.forget(id, lvl);
				npcByEntityId.remove(s.avatar().getId());
				npcByEntityId.remove(s.panel().getId());
				lvl.removeEntity(s.avatar().getId(), Entity.RemovalReason.DISCARDED);
				lvl.removeEntity(s.panel().getId(), Entity.RemovalReason.DISCARDED);
			}
		}

		// Group into neighbourhoods; most urgent neighbourhood first.
		Map<String, List<NpcInfo>> groups = new LinkedHashMap<>();
		List<NpcInfo> ordered = new ArrayList<>(wanted.values());
		ordered.sort(Comparator.comparingInt(NpcInfo::priority).thenComparing(NpcInfo::id));
		for (NpcInfo n : ordered) {
			groups.computeIfAbsent(n.groupId(), k -> new ArrayList<>()).add(n);
		}

		for (String gid : new ArrayList<>(signs.keySet())) {
			if (!groups.containsKey(gid)) {
				lvl.removeEntity(signs.remove(gid).getId(), Entity.RemovalReason.DISCARDED);
			}
		}

		int g = 0;
		for (Map.Entry<String, List<NpcInfo>> group : groups.entrySet()) {
			List<NpcInfo> members = group.getValue();
			Vec3 center = a.add((g % PLOTS_PER_ROW) * PLOT, 0, (g / PLOTS_PER_ROW) * PLOT);
			g++;
			NpcInfo first = members.get(0);
			Display.TextDisplay sign = signs.get(group.getKey());
			if (sign == null || sign.isRemoved()) {
				sign = textDisplay(lvl, 0xA0000000);
				signs.put(group.getKey(), sign);
			}
			sign.snapTo(center.x, center.y + 3.6, center.z, 0f, 0f);
			setText(sign, Nameplate.sign(first.groupLabel(), first.envLabel(), members.size()));

			double radius = Math.max(2.0, Math.min(PLOT / 2 - 1, members.size() * 0.55));
			for (int i = 0; i < members.size(); i++) {
				NpcInfo info = members.get(i);
				double angle = (Math.PI * 2 * i) / Math.max(1, members.size());
				Vec3 pos = new Vec3(center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius);
				Spawned s = spawned.get(info.id());
				if (s == null || s.avatar().isRemoved()) {
					s = spawn(lvl, info, pos);
					spawned.put(info.id(), s);
					npcByEntityId.put(s.avatar().getId(), info.id());
					npcByEntityId.put(s.panel().getId(), info.id());
				}
				// Once spawned, the BehaviorDirector moves the avatar and its panel.
				s.avatar().update(info);
				Component extra = panelExtra.apply(info.id());
				setText(s.panel(), extra == null ? Nameplate.info(info) : Nameplate.info(info).copy().append(extra));
			}
		}
	}

	private static void setText(Display.TextDisplay d, Component text) {
		if (!Objects.equals(d.getText(), text)) {
			d.setText(text);
		}
	}

	private static Display.TextDisplay textDisplay(ClientLevel lvl, int background) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
		d.setId(nextClientId--);
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setBackgroundColor(background);
		d.setLineWidth(220);
		lvl.addEntity(d);
		return d;
	}

	private static Spawned spawn(ClientLevel lvl, NpcInfo info, Vec3 pos) {
		ThreadAvatar avatar = new ThreadAvatar(lvl, info.id());
		avatar.setId(nextClientId--);
		avatar.setNoGravity(true);
		avatar.setSilent(true);
		avatar.update(info);
		avatar.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
		lvl.addEntity(avatar);
		Display.TextDisplay panel = textDisplay(lvl, 0x60000000);
		panel.snapTo(pos.x, pos.y + 2.45, pos.z, 0f, 0f);
		T3Craft.LOGGER.debug("spawned client-only NPC {} at {}", info.id(), pos);
		return new Spawned(avatar, panel);
	}
}
