package dev.t3craft.build.client;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A builder: a client-only {@link RemotePlayer} rendered by the vanilla player renderer, same
 * approach as {@code dev.t3craft.client.npc.ThreadAvatar} (no registered entity type, no network
 * traffic). Not a subclass of it because that class is final and tied to thread NPC data; see
 * docs/feat-builders.md.
 *
 * <p>Movement happens inside {@link #tick()}, after the level stored the old position, so the
 * renderer interpolates smoothly and the vanilla walk animation (computed from the position delta)
 * plays.
 */
public final class BuilderAvatar extends RemotePlayer {
	/** Ground height at a column (block y the feet stand on top of, i.e. heightmap value). */
	@FunctionalInterface
	public interface Ground {
		double feetY(double x, double z);
	}

	private static final double STEP_Y_PER_TICK = 0.35;
	/** Ticks without progress before a builder on a path is put at its goal (only way out of a jam). */
	static final int STUCK_TICKS = 60;

	private final Component label;
	private final UUID skinId;
	private final Ground ground;
	private @Nullable Vec3 target;
	private @Nullable Vec3 lookAt;
	private double speed = 0.22;
	private boolean swingWhenClose;
	/** Waypoints still to walk (A* path, feet positions); the last one is {@link #target}. */
	private final java.util.ArrayDeque<Vec3> path = new java.util.ArrayDeque<>();
	private int stuckTicks;

	public BuilderAvatar(ClientLevel level, String seed, String name, Ground ground) {
		super(level, new GameProfile(uuid("t3craft-builder:" + seed), "builder"));
		this.label = Component.literal(name).withStyle(ChatFormatting.GOLD);
		this.skinId = uuid("t3craft-builder-skin:" + seed);
		this.ground = ground;
		setNoGravity(true);
		setSilent(true);
	}

	static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}

	/** Walk toward {@code pos} (feet position) at {@code blocksPerTick}. */
	public void walkTo(Vec3 pos, double blocksPerTick) {
		this.path.clear();
		this.target = pos;
		this.speed = blocksPerTick;
		this.stuckTicks = 0;
	}

	/** Walk along {@code waypoints} (feet positions, e.g. an A* path over the real blocks) to the last one. */
	public void follow(java.util.List<Vec3> waypoints, double blocksPerTick) {
		if (waypoints.isEmpty()) {
			return;
		}
		this.path.clear();
		this.path.addAll(waypoints);
		this.target = waypoints.getLast();
		this.speed = blocksPerTick;
		this.stuckTicks = 0;
	}

	/** Shortcut to a far work spot: put at {@code pos} (feet) right away, nothing left to walk. */
	public void leapTo(Vec3 pos) {
		this.path.clear();
		this.target = pos;
		this.stuckTicks = 0;
		snapTo(pos.x, pos.y, pos.z, getYRot(), 0f);
	}

	public void lookAt(@Nullable Vec3 point) {
		this.lookAt = point;
	}

	public @Nullable Vec3 target() {
		return target;
	}

	public boolean arrived(double within) {
		return target == null || horizontalDistance(position(), target) <= within;
	}

	/** Holds {@code stack} and swings at {@link #lookAt} now, or as soon as it is within reach. */
	public void place(ItemStack stack) {
		setItemInHand(InteractionHand.MAIN_HAND, stack);
		if (withinReach()) {
			swingArm();
		} else {
			swingWhenClose = true;
		}
	}

	private boolean withinReach() {
		return lookAt == null || position().add(0, getEyeHeight(), 0).distanceToSqr(lookAt) <= 5.5 * 5.5;
	}

	private void swingArm() {
		swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		swingWhenClose = false;
	}

	@Override
	public void tick() {
		Vec3 pos = position();
		float yaw = getYRot();
		boolean moving = false;
		if (!path.isEmpty()) {
			moving = followPath(pos);
			if (moving) {
				Vec3 head = path.peekFirst();
				if (head != null) {
					yaw = (float) (Mth.atan2(head.z - pos.z, head.x - pos.x) * (180 / Math.PI)) - 90f;
				}
			}
		} else if (target != null) {
			double dx = target.x - pos.x;
			double dz = target.z - pos.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			double nx = pos.x;
			double nz = pos.z;
			if (dist > 0.05) {
				double step = Math.min(speed, dist);
				nx += dx / dist * step;
				nz += dz / dist * step;
				yaw = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90f;
				moving = true;
			}
			// Follow the terrain while walking, settle on the target height at the spot.
			double wantY = dist > 1.5 ? Math.max(ground.feetY(nx, nz), target.y - 2.0) : target.y;
			double ny = pos.y + Mth.clamp(wantY - pos.y, -STEP_Y_PER_TICK, STEP_Y_PER_TICK);
			setPos(nx, ny, nz);
		}
		if (!moving && lookAt != null) {
			Vec3 d = lookAt.subtract(position().add(0, getEyeHeight(), 0));
			yaw = (float) (Mth.atan2(d.z, d.x) * (180 / Math.PI)) - 90f;
			setXRot((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180 / Math.PI)));
		} else if (moving) {
			setXRot(0f);
		}
		if (swingWhenClose && withinReach()) {
			swingArm();
		}
		super.tick();
		setYRot(yaw);
		setYHeadRot(yaw);
		setYBodyRot(yaw);
	}

	/** One tick along the path: straight to the next waypoint, its height followed smoothly. */
	private boolean followPath(Vec3 pos) {
		Vec3 head = path.peekFirst();
		while (head != null && horizontalDistance(pos, head) < 0.08 && Math.abs(pos.y - head.y) < 0.4) {
			path.pollFirst();
			head = path.peekFirst();
		}
		if (head == null) {
			return false;
		}
		double dx = head.x - pos.x;
		double dz = head.z - pos.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double step = Math.min(speed, dist);
		double nx = dist < 1e-6 ? pos.x : pos.x + dx / dist * step;
		double nz = dist < 1e-6 ? pos.z : pos.z + dz / dist * step;
		double ny = pos.y + Mth.clamp(head.y - pos.y, -STEP_Y_PER_TICK, STEP_Y_PER_TICK);
		if (Math.abs(nx - pos.x) + Math.abs(nz - pos.z) + Math.abs(ny - pos.y) < 0.005) {
			if (++stuckTicks >= STUCK_TICKS && target != null) {
				snapTo(target.x, target.y, target.z, getYRot(), 0f); // out of a jam (the other teleport is leapTo)
				path.clear();
				stuckTicks = 0;
				return false;
			}
		} else {
			stuckTicks = 0;
		}
		setPos(nx, ny, nz);
		return true;
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	@Override
	public Component getDisplayName() {
		return label;
	}

	@Override
	public PlayerSkin getSkin() {
		return DefaultPlayerSkin.get(skinId);
	}
}
