package dev.t3craft.client.npc;

import dev.t3craft.T3Craft;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Client-only NPC registry: one vanilla {@link Villager} per T3 thread, added straight into the
 * {@link ClientLevel} with a negative entity id (never sent to or known by any server). The
 * wanted set comes from the bridge; {@link #tick} reconciles it with the entities in the level.
 * All methods run on the client thread.
 *
 * <p>Layout (phase 1): NPCs ordered by the bridge's {@code priority}, on a ring "corral" anchored where the player stood when the first NPC
 * appeared in this level. TODO(phase 5): configurable anchor / stations / pathfinding.
 */
public final class NpcRegistry {
	/** Client-only ids count down from here, far away from server-assigned (positive) ids. */
	private static int nextClientId = -1_000_000;

	private final Map<String, NpcInfo> wanted = new LinkedHashMap<>();
	private final Map<String, Villager> spawned = new LinkedHashMap<>();
	private final Map<Integer, String> threadByEntityId = new LinkedHashMap<>();
	private @Nullable ClientLevel level;
	private @Nullable Vec3 anchor;
	private boolean dirty;

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

	public @Nullable NpcInfo info(String threadId) {
		return wanted.get(threadId);
	}

	/** Thread id for one of our client-only entities, or null if the entity is not ours. */
	public @Nullable String threadFor(Entity entity) {
		return threadByEntityId.get(entity.getId());
	}

	public int size() {
		return wanted.size();
	}

	public void tick(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			// World changed or left: old entities died with the old level.
			spawned.clear();
			threadByEntityId.clear();
			level = lvl;
			anchor = null;
			dirty = true;
		}
		if (lvl == null || mc.player == null || !dirty) {
			return;
		}
		dirty = false;
		if (anchor == null) {
			anchor = mc.player.position().add(4, 0, 4);
		}

		for (String id : new ArrayList<>(spawned.keySet())) {
			if (!wanted.containsKey(id)) {
				Villager v = spawned.remove(id);
				threadByEntityId.remove(v.getId());
				lvl.removeEntity(v.getId(), Entity.RemovalReason.DISCARDED);
			}
		}

		List<NpcInfo> ordered = new ArrayList<>(wanted.values());
		ordered.sort(java.util.Comparator.comparingInt(NpcInfo::priority).thenComparing(NpcInfo::id));
		int i = 0;
		int count = ordered.size();
		for (NpcInfo info : ordered) {
			Vec3 pos = slot(i++, count);
			Villager v = spawned.get(info.id());
			if (v == null || v.isRemoved()) {
				v = spawn(lvl, pos);
				spawned.put(info.id(), v);
				threadByEntityId.put(v.getId(), info.id());
			} else if (v.position().distanceToSqr(pos) > 0.01) {
				v.snapTo(pos.x, pos.y, pos.z, v.getYRot(), 0f);
			}
			if (!Objects.equals(v.getCustomName(), Nameplate.of(info))) {
				v.setCustomName(Nameplate.of(info));
			}
		}
	}

	public void clear(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != null) {
			for (Villager v : spawned.values()) {
				lvl.removeEntity(v.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		spawned.clear();
		threadByEntityId.clear();
		wanted.clear();
	}

	private Vec3 slot(int index, int count) {
		Vec3 a = Objects.requireNonNull(anchor);
		double radius = Math.max(3.0, count * 0.6);
		double angle = (Math.PI * 2 * index) / Math.max(1, count);
		return new Vec3(a.x + Math.cos(angle) * radius, a.y, a.z + Math.sin(angle) * radius);
	}

	private static Villager spawn(ClientLevel lvl, Vec3 pos) {
		Villager v = new Villager(EntityTypes.VILLAGER, lvl);
		v.setId(nextClientId--);
		v.setNoAi(true);
		v.setSilent(true);
		v.setNoGravity(true);
		v.setCustomNameVisible(true);
		v.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
		lvl.addEntity(v);
		T3Craft.LOGGER.debug("spawned client-only NPC {} at {}", v.getId(), pos);
		return v;
	}
}
