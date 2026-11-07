package dev.t3craft.client.npc;

import dev.t3craft.T3Craft;
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
import net.minecraft.util.Mth;
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
	private static int nextClientId = -1_000_000;

	private record Spawned(ThreadAvatar avatar, Display.TextDisplay panel) {
	}

	private final Map<String, NpcInfo> wanted = new LinkedHashMap<>();
	private final Map<String, Spawned> spawned = new LinkedHashMap<>();
	private final Map<String, Display.TextDisplay> signs = new LinkedHashMap<>();
	private final Map<Integer, String> npcByEntityId = new HashMap<>();
	private @Nullable ClientLevel level;
	private @Nullable Vec3 anchor;
	private boolean dirty;
	private int tick;

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

	public void tick(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			// World changed or left: old entities died with the old level.
			spawned.clear();
			signs.clear();
			npcByEntityId.clear();
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
		for (Map.Entry<String, Spawned> e : spawned.entrySet()) {
			ThreadAvatar a = e.getValue().avatar();
			lookAt(a, mc.player.position().add(0, mc.player.getEyeHeight(), 0));
			NpcInfo info = wanted.get(e.getKey());
			if (info != null && (tick + Math.abs(a.getId())) % PARTICLE_EVERY_TICKS == 0) {
				SimpleParticleType p = particle(info.particle());
				if (p != null) {
					lvl.addParticle(p, a.getX() + (a.getRandom().nextDouble() - 0.5) * 0.6, a.getY() + 2.25, a.getZ() + (a.getRandom().nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
				}
			}
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
				} else if (s.avatar().position().distanceToSqr(pos) > 0.01) {
					s.avatar().snapTo(pos.x, pos.y, pos.z, s.avatar().getYRot(), 0f);
				}
				s.avatar().update(info);
				s.panel().snapTo(pos.x, pos.y + 2.45, pos.z, 0f, 0f);
				setText(s.panel(), Nameplate.info(info));
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

	private static void lookAt(ThreadAvatar a, Vec3 eye) {
		Vec3 head = a.position().add(0, a.getEyeHeight(), 0);
		Vec3 d = eye.subtract(head);
		if (d.lengthSqr() > 12 * 12) {
			return;
		}
		float yaw = (float) (Mth.atan2(d.z, d.x) * (180 / Math.PI)) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180 / Math.PI));
		a.setYHeadRot(yaw);
		a.setYBodyRot(yaw);
		a.setXRot(pitch);
	}
}
