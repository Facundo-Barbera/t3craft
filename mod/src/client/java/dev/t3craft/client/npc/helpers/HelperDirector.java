package dev.t3craft.client.npc.helpers;

import dev.t3craft.T3Craft;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Subagents as "helpers": small client-only players in the apprentice variant of the parent's skin ({@link HelperAvatar}) that follow their
 * parent's NPC and stand on an arc behind it ({@link HelperFormation}), so they gather round the
 * parent's desk without taking one. Fed once per refresh with the grouping from
 * {@link HelperRoster}; spawns, despawns (with particles) and re-slots through
 * {@link HelperLifecycle}. Client thread only; {@link #tick} never throws.
 *
 * <p><b>Entity ids</b> count down from {@link #ID_BASE} = −4,000,000 (NpcRegistry −1,000,000…,
 * builders −2,000,000…, BehaviorDirector −3,000,000…, workstations −5,000,000…). NpcRegistry's
 * orphan sweep treats every id ≤ −1,000,000 as its own and may discard our helpers after one of
 * its rare recoveries: each tick checks {@link Entity#isRemoved()} and respawns them. Entities are
 * kept by reference, never looked up by id and cast (hot-swap safe); no static state.
 *
 * <p>Parent positions come from {@code parentAvatar} (the registry's avatar for an NPC id). A helper
 * whose parent has not spawned yet waits, invisible, until it has.
 */
public final class HelperDirector {
	static final int ID_BASE = -4_000_000;
	private static final int PARTICLE_EVERY_TICKS = 20;
	private static final long ERROR_LOG_INTERVAL_MS = 30_000;
	/** Top of a helper's head (player height × HelperAvatar.SCALE), for the status-colour particles. */
	private static final double HEAD_Y = 1.1;

	private static final class Body {
		HelperLifecycle.Placed placed;
		@Nullable HelperAvatar avatar;
		HelperFormation.@Nullable Spot at;
		float yaw;

		Body(HelperLifecycle.Placed placed) {
			this.placed = placed;
		}
	}

	private final HelperLifecycle life = new HelperLifecycle();
	private final Map<String, Body> bodies = new LinkedHashMap<>();
	private final Map<Integer, String> parentByEntity = new HashMap<>();
	private Map<String, HelperRoster.Family> families = Map.of();
	private Set<String> running = Set.of();
	private List<String> parents = List.of();
	private boolean dirty;
	private @Nullable ClientLevel level;
	private int nextId = ID_BASE;
	private int tick;
	private long lastErrorLogMs;

	/**
	 * New data (client thread). {@code visibleParents} = NPC ids that have an NPC now, in roster order;
	 * pass {@link HelperRoster.Collected#EMPTY} to drop every helper (helpers off, bridge mode).
	 */
	public void update(HelperRoster.Collected collected, Collection<String> visibleParents, int maxPerParent) {
		parents = List.copyOf(visibleParents);
		families = HelperRoster.group(collected.active(), parents, maxPerParent);
		running = collected.activeIds();
		dirty = true;
	}

	/** Text appended to a parent's panel ("⚒ 2 helpers +3"), or null. For {@code NpcRegistry.setPanelExtra}. */
	public @Nullable Component panelExtra(String parentNpcId) {
		String line = HelperRoster.panelLine(families.get(parentNpcId));
		return line == null ? null : Component.literal("\n" + line).withStyle(ChatFormatting.GOLD);
	}

	/** NPC id of the parent a helper entity follows (right-click opens the parent), or null if not ours. */
	public @Nullable String parentFor(Entity entity) {
		return parentByEntity.get(entity.getId());
	}

	public int size() {
		return bodies.size();
	}

	/** One client tick, after the registry's. Never throws. */
	public void tick(Minecraft mc, Function<String, @Nullable LivingEntity> parentAvatar) {
		try {
			tickUnsafe(mc, parentAvatar);
		} catch (VirtualMachineError e) {
			if (!(e instanceof StackOverflowError)) {
				throw e;
			}
			recover(mc.level, e);
		} catch (Throwable e) {
			recover(mc.level, e);
		}
	}

	/** Remove every helper now (no particles) and forget them. */
	public void clear(@Nullable ClientLevel lvl) {
		for (Body b : bodies.values()) {
			discard(lvl, b.avatar);
		}
		bodies.clear();
		parentByEntity.clear();
		life.reset();
		dirty = true;
	}

	private void recover(@Nullable ClientLevel lvl, Throwable error) {
		long now = System.currentTimeMillis();
		if (now - lastErrorLogMs > ERROR_LOG_INTERVAL_MS) {
			lastErrorLogMs = now;
			T3Craft.LOGGER.warn("T3Craft helper tick failed; rebuilding helpers", error);
		}
		try {
			clear(lvl);
		} catch (Throwable ignored) {
			// best effort; next tick starts from clean maps anyway
		}
	}

	private void tickUnsafe(Minecraft mc, Function<String, @Nullable LivingEntity> parentAvatar) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			// Old entities died with the old level.
			clear(null);
			level = lvl;
		}
		if (lvl == null) {
			return;
		}
		if (!dev.t3craft.client.hq.HqPresence.allows(mc)) {
			// fix/hq-gate-npcs: no helper outside the HQ's world/dimension; they come back with the gate.
			for (Body b : bodies.values()) {
				if (b.avatar != null) {
					discard(lvl, b.avatar);
					b.avatar = null;
				}
			}
			parentByEntity.clear();
			return;
		}
		tick++;
		if (dirty) {
			dirty = false;
			apply(lvl, life.update(families, running, parents));
		}
		for (Body b : bodies.values()) {
			LivingEntity parent = parentAvatar.apply(b.placed.parentId());
			if (parent == null || parent.isRemoved()) {
				continue;
			}
			if (b.avatar == null || b.avatar.isRemoved()) {
				spawn(lvl, b, parent);
			}
			follow(b, parent);
			HelperAvatar a = b.avatar;
			if (a != null && (tick + Math.abs(a.getId())) % PARTICLE_EVERY_TICKS == 0) {
				ParticleOptions p = new DustParticleOptions(b.placed.view().color(), 0.7f);
				lvl.addParticle(p, a.getX(), a.getY() + HEAD_Y + 0.35, a.getZ(), 0, 0.02, 0);
			}
		}
	}

	private void apply(ClientLevel lvl, HelperLifecycle.Diff diff) {
		for (HelperLifecycle.Despawn d : diff.despawned()) {
			Body b = bodies.remove(d.last().view().id());
			if (b == null || b.avatar == null) {
				continue;
			}
			HelperAvatar a = b.avatar;
			parentByEntity.remove(a.getId());
			if (!a.isRemoved()) {
				burst(lvl, a.position(), d.reason());
			}
			discard(lvl, a);
		}
		for (HelperLifecycle.Placed p : diff.spawned()) {
			bodies.put(p.view().id(), new Body(p));
		}
		for (HelperLifecycle.Placed p : diff.updated()) {
			Body b = bodies.get(p.view().id());
			if (b == null) {
				bodies.put(p.view().id(), new Body(p));
				continue;
			}
			b.placed = p;
			if (b.avatar != null) {
				b.avatar.update(p.view());
				parentByEntity.put(b.avatar.getId(), p.parentId());
			}
		}
	}

	private void spawn(ClientLevel lvl, Body b, LivingEntity parent) {
		if (b.avatar != null) {
			parentByEntity.remove(b.avatar.getId());
			discard(lvl, b.avatar);
		}
		HelperAvatar a = new HelperAvatar(lvl, b.placed.view().id());
		a.setId(nextId--);
		a.update(b.placed.view());
		HelperFormation.Spot s = target(b, parent);
		boolean fresh = b.at == null;
		b.at = s;
		b.yaw = parent.yBodyRot;
		a.snapTo(s.x(), parent.getY(), s.z(), b.yaw, 0f);
		a.setYHeadRot(b.yaw);
		a.setYBodyRot(b.yaw);
		lvl.addEntity(a);
		b.avatar = a;
		parentByEntity.put(a.getId(), b.placed.parentId());
		if (fresh) {
			// The subagent just started: a little puff where it appears.
			for (int i = 0; i < 6; i++) {
				lvl.addParticle(ParticleTypes.POOF, s.x() + jitter(a), parent.getY() + 0.4 + a.getRandom().nextDouble() * 0.6, s.z() + jitter(a), 0, 0.03, 0);
			}
		}
	}

	private static HelperFormation.Spot target(Body b, LivingEntity parent) {
		return HelperFormation.spot(parent.getX(), parent.getZ(), parent.yBodyRot, b.placed.index(), b.placed.count());
	}

	private void follow(Body b, LivingEntity parent) {
		HelperAvatar a = b.avatar;
		if (a == null) {
			return;
		}
		HelperFormation.Spot target = target(b, parent);
		HelperFormation.Spot from = b.at == null ? target : b.at;
		HelperFormation.Spot next = HelperFormation.step(from, target);
		HelperFormation.Spot parentSpot = new HelperFormation.Spot(parent.getX(), parent.getZ());
		boolean moving = next != from;
		// Walking: face where it goes. Standing: turn (slowly) to look at the parent.
		float want = moving ? HelperFormation.yawTowards(from, next, b.yaw) : HelperFormation.yawTowards(next, parentSpot, b.yaw);
		b.yaw = HelperFormation.lerpYaw(b.yaw, want, moving ? 0.5f : 0.15f);
		b.at = next;
		Vec3 pos = new Vec3(next.x(), parent.getY(), next.z());
		if (next.equals(target) && from.dist(target) >= HelperFormation.SNAP_DISTANCE) {
			a.snapTo(pos.x, pos.y, pos.z, b.yaw, 0f);
		} else {
			a.moveOrInterpolateTo(pos, b.yaw, 0f);
		}
		a.setYBodyRot(b.yaw);
		a.setYHeadRot(b.yaw);
	}

	private static void burst(ClientLevel lvl, Vec3 at, HelperLifecycle.Reason reason) {
		ParticleOptions extra = switch (reason) {
			case FINISHED -> ParticleTypes.HAPPY_VILLAGER;
			case PARENT_GONE -> ParticleTypes.SMOKE;
			case HIDDEN -> null;
		};
		var rnd = lvl.getRandom();
		for (int i = 0; i < 10; i++) {
			double dx = (rnd.nextDouble() - 0.5) * 0.6;
			double dz = (rnd.nextDouble() - 0.5) * 0.6;
			lvl.addParticle(ParticleTypes.POOF, at.x + dx, at.y + 0.3 + rnd.nextDouble() * 0.7, at.z + dz, 0, 0.04, 0);
			if (extra != null && i % 2 == 0) {
				lvl.addParticle(extra, at.x + dx, at.y + 0.8 + rnd.nextDouble() * 0.4, at.z + dz, 0, 0.02, 0);
			}
		}
	}

	private static double jitter(Entity e) {
		return (e.getRandom().nextDouble() - 0.5) * 0.5;
	}

	private static void discard(@Nullable ClientLevel lvl, @Nullable Entity e) {
		if (lvl != null && e != null) {
			lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
		}
	}
}
