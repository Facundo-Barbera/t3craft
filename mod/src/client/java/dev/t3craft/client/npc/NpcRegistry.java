package dev.t3craft.client.npc;

import dev.t3craft.T3Craft;
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
 * <p>Layout without an HQ: a {@link GridLayout} next to where the player stood when the first NPC
 * appeared in this level: one block of desks per neighbourhood, in rows with aisles, signs over
 * each block's header strip. Cells are stable (adding a thread never moves the others). With an
 * HQ the behaviour director walks NPCs to its desks instead. Particles over the head by state.
 * Avatars turn their head to a nearby player.
 */
public final class NpcRegistry {
	/** Sign height above its header strip's ground. */
	private static final double SIGN_Y = 2.6;
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
	/** Near-panel text per NPC (compact mode shows it only when {@link NpcLabels.Visibility} says so). */
	private final Map<String, Component> panelText = new HashMap<>();
	private final Map<String, NpcLabels.Visibility> detail = new HashMap<>();
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

	/** Left-click on an NPC: toggle its detail panel (compact labels). */
	public void toggleDetail(String npcId) {
		detail.computeIfAbsent(npcId, k -> new NpcLabels.Visibility()).toggle();
	}

	/** Open an NPC's detail panel for a while (e.g. when its chat is opened). */
	public void revealDetail(String npcId) {
		detail.computeIfAbsent(npcId, k -> new NpcLabels.Visibility()).reveal();
	}

	/**
	 * Origin of the fallback layout (where the player stood when the first NPC appeared in this
	 * level, offset by 4,0,4), or null before that. Read by feat/workstation to put the provisional
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
			panelText.clear();
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
			panelText.clear();
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
		if (anchor != null) {
			behavior.tick(lvl, mc.player, fallbackLayout(), bodies);
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
		panelText.clear();
		detail.clear();
		wanted.clear();
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

	private void reconcile(ClientLevel lvl, Vec3 a) {
		// Remove NPCs no longer wanted.
		for (String id : new ArrayList<>(spawned.keySet())) {
			if (!wanted.containsKey(id)) {
				Spawned s = spawned.remove(id);
				behavior.forget(id, lvl);
				npcByEntityId.remove(s.avatar().getId());
				npcByEntityId.remove(s.panel().getId());
				panelText.remove(id);
				detail.remove(id);
				lvl.removeEntity(s.avatar().getId(), Entity.RemovalReason.DISCARDED);
				lvl.removeEntity(s.panel().getId(), Entity.RemovalReason.DISCARDED);
			}
		}

		// Provisional desks: stable grid cells, one block of desks per neighbourhood.
		List<GridLayout.Member> members = new ArrayList<>(wanted.size());
		for (NpcInfo n : wanted.values()) {
			members.add(new GridLayout.Member(n.id(), n.groupId(), n.envLabel()));
		}
		fallbackLayout().update(new Pos(a.x, a.y, a.z), members, BehaviorDirector.ground(lvl, a.y));

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

		for (Map.Entry<String, List<NpcInfo>> group : groups.entrySet()) {
			List<NpcInfo> inGroup = group.getValue();
			NpcInfo first = inGroup.get(0);
			Pos header = fallbackLayout().header(group.getKey());
			Display.TextDisplay sign = signs.get(group.getKey());
			if (sign == null || sign.isRemoved()) {
				sign = textDisplay(lvl, 0xA0000000);
				signs.put(group.getKey(), sign);
			}
			if (header != null) {
				sign.snapTo(header.x(), header.y() + SIGN_Y, header.z(), 0f, 0f);
			}
			setText(sign, Nameplate.sign(first.groupLabel(), first.envLabel(), inGroup.size()));

			for (NpcInfo info : inGroup) {
				Anchor desk = fallbackLayout().desk(info.id(), info.groupId());
				Vec3 pos = desk == null ? a : new Vec3(desk.pos().x(), desk.pos().y(), desk.pos().z());
				Spawned s = spawned.get(info.id());
				if (s == null || s.avatar().isRemoved()) {
					s = spawn(lvl, info, pos, desk == null ? 0f : desk.yaw());
					spawned.put(info.id(), s);
					npcByEntityId.put(s.avatar().getId(), info.id());
					npcByEntityId.put(s.panel().getId(), info.id());
				}
				// Once spawned, the BehaviorDirector moves the avatar and its panel.
				s.avatar().update(info);
				Component extra = panelExtra.apply(info.id());
				Component body = compactLabels ? Nameplate.detail(info, ChipsConfig.current()) : Nameplate.info(info);
				panelText.put(info.id(), extra == null ? body : body.copy().append(extra));
			}
		}
		updatePanels(null);
	}

	/**
	 * Applies each panel's text: always on in detailed mode; in compact mode only while the player
	 * is near, aims at the NPC or clicked it (hysteresis in {@link NpcLabels.Visibility}). With
	 * {@code mc == null} (from reconcile) only re-applies the current visibility.
	 */
	private void updatePanels(@Nullable Minecraft mc) {
		LabelConfig cfg = LabelConfig.current();
		if (cfg.compact() != compactLabels) {
			compactLabels = cfg.compact();
			dirty = true; // rebuild name tags and panel texts for the new mode
		}
		Vec3 eye = mc == null || mc.player == null ? null : mc.player.getEyePosition();
		Vec3 look = mc == null || mc.player == null ? null : mc.player.getViewVector(1f);
		for (Map.Entry<String, Spawned> e : spawned.entrySet()) {
			Display.TextDisplay panel = e.getValue().panel();
			Component text = panelText.get(e.getKey());
			boolean show = text != null;
			if (show && compactLabels) {
				NpcLabels.Visibility v = detail.computeIfAbsent(e.getKey(), k -> new NpcLabels.Visibility());
				if (eye != null) {
					ThreadAvatar a = e.getValue().avatar();
					double dist = a.position().distanceTo(mc.player.position());
					double aim = NpcLabels.aimMiss(eye.x, eye.y, eye.z, look.x, look.y, look.z,
							a.getX(), a.getY() + 1.2, a.getZ(), NpcLabels.AIM_RANGE);
					show = v.update(dist, aim, cfg.detailDistance());
				} else {
					show = v.shown();
				}
			}
			Component want = show ? text : Component.empty();
			if (!Objects.equals(panel.getText(), want)) {
				panel.setText(want);
				panel.setBackgroundColor(show ? 0x60000000 : 0);
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

	private static Spawned spawn(ClientLevel lvl, NpcInfo info, Vec3 pos, float yaw) {
		ThreadAvatar avatar = new ThreadAvatar(lvl, info.id());
		avatar.setId(nextClientId--);
		avatar.setNoGravity(true);
		avatar.setSilent(true);
		avatar.update(info);
		avatar.snapTo(pos.x, pos.y, pos.z, yaw, 0f);
		lvl.addEntity(avatar);
		Display.TextDisplay panel = textDisplay(lvl, 0);
		panel.snapTo(pos.x, pos.y + 2.45, pos.z, 0f, 0f);
		T3Craft.LOGGER.debug("spawned client-only NPC {} at {}", info.id(), pos);
		return new Spawned(avatar, panel);
	}
}
