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

	private final Component label;
	private final UUID skinId;
	private final Ground ground;
	private @Nullable Vec3 target;
	private @Nullable Vec3 lookAt;
	private double speed = 0.22;
	private boolean swingWhenClose;

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
		this.target = pos;
		this.speed = blocksPerTick;
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
		if (target != null) {
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
