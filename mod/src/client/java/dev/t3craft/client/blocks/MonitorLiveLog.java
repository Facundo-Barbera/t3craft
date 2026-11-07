package dev.t3craft.client.blocks;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.MonitorBlock;
import dev.t3craft.blocks.MonitorBlockEntity;
import dev.t3craft.client.chat.ChatScreens;
import dev.t3craft.client.chat.ChatSource;
import dev.t3craft.client.chat.ChatTranscript;
import dev.t3craft.client.chat.ThreadFeed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Glue between the world and the live log: tracks the loaded monitors (block entity load/unload),
 * measures each one against the camera every {@value #EVERY_TICKS} ticks (distance to the screen
 * face, in view, facing the camera, optionally a line of sight), runs {@link MonitorVisibility} and
 * hands the wanted thread keys to {@link MonitorFeeds}, which opens one read-only
 * {@link ThreadFeed} per thread through the chat's {@link ChatScreens#defaultSource() source} (the
 * environment's shared connection; no socket of its own).
 */
final class MonitorLiveLog implements AutoCloseable {
	static final int EVERY_TICKS = 5;
	/** Degrees added to the view's half-angle (a monitor at the screen edge still counts). */
	static final double CONE_MARGIN = 15;
	/** Closer than this the monitor counts as visible whatever the view direction. */
	static final double ALWAYS_VISIBLE = 2.5;

	private final Map<Long, MonitorBlockEntity> loaded = new HashMap<>();
	private final MonitorVisibility visibility;
	private final MonitorFeeds feeds = new MonitorFeeds(MonitorLiveLog::open);
	private MonitorSettings settings;
	private int ticks;

	MonitorLiveLog(MonitorSettings settings) {
		this.settings = settings;
		this.visibility = new MonitorVisibility(settings);
	}

	void settings(MonitorSettings s) {
		settings = s;
		visibility.settings(s);
	}

	MonitorFeeds feeds() {
		return feeds;
	}

	MonitorVisibility visibility() {
		return visibility;
	}

	int loaded() {
		return loaded.size();
	}

	void onLoad(BlockEntity be) {
		if (be instanceof MonitorBlockEntity m) {
			loaded.put(m.getBlockPos().asLong(), m);
		}
	}

	void onUnload(BlockEntity be) {
		if (be instanceof MonitorBlockEntity m) {
			loaded.remove(m.getBlockPos().asLong(), m);
		}
	}

	/** One round (every {@value #EVERY_TICKS} ticks): measure, decide, open/close feeds. */
	void tick(Minecraft mc, BiFunction<BlockPos, String, Binding> resolve, Function<MonitorBlockEntity, ScreenExtent> extents) {
		if (++ticks % EVERY_TICKS != 0) {
			return;
		}
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 eye = cam.position();
		Vector3fc f = cam.forwardVector();
		double fov = mc.options.fov().get();
		double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
		double half = Math.max(fov / 2, MonitorVisibility.horizontalHalfAngle(fov, aspect)) + CONE_MARGIN;
		double keep = settings.subscribeRadius() + settings.releaseHysteresis();
		List<MonitorVisibility.Observation> obs = new ArrayList<>(loaded.size());
		loaded.values().removeIf(m -> m.isRemoved() || m.getLevel() != level);
		for (MonitorBlockEntity m : loaded.values()) {
			BlockPos pos = m.getBlockPos();
			ScreenExtent extent = extents.apply(m);
			if (!extent.origin()) {
				continue; // a panel is measured (and subscribed) once, through its origin block
			}
			Binding b = resolve.apply(pos, m.binding());
			String key = b.kind() == Binding.Kind.THREAD ? b.value() : null;
			Direction facing = m.getBlockState().hasProperty(MonitorBlock.FACING) ? m.getBlockState().getValue(MonitorBlock.FACING) : Direction.NORTH;
			Vec3 face = MonitorRenderer.screenCenter(pos, facing, extent);
			double d = face.distanceTo(eye);
			boolean visible = key != null && d <= keep && visible(level, eye, f, half, face, facing, pos, d, mc);
			obs.add(new MonitorVisibility.Observation(pos.asLong(), key, d, visible));
		}
		Set<String> wanted = visibility.update(obs, System.currentTimeMillis());
		feeds.retain(wanted, System.currentTimeMillis());
	}

	private boolean visible(ClientLevel level, Vec3 eye, Vector3fc f, double half, Vec3 face, Direction facing, BlockPos pos, double d, Minecraft mc) {
		if (settings.visibility() == MonitorSettings.Visibility.DISTANCE) {
			return true;
		}
		Vec3 toEye = eye.subtract(face);
		// the screen is only on the front: from behind (or edge-on) there is nothing to read
		if (toEye.x * facing.getStepX() + toEye.z * facing.getStepZ() <= 0.05) {
			return false;
		}
		if (d > ALWAYS_VISIBLE && !MonitorVisibility.inCone(face.x - eye.x, face.y - eye.y, face.z - eye.z, f.x(), f.y(), f.z(), half)) {
			return false;
		}
		if (settings.visibility() != MonitorSettings.Visibility.LINE_OF_SIGHT || mc.player == null) {
			return true;
		}
		Vec3 target = face.add(facing.getStepX() * 0.05, 0, facing.getStepZ() * 0.05);
		BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	/** Opens one thread's read-only stream; null when the environment is not reachable directly now. */
	static MonitorFeeds.@Nullable Feed open(String threadKey) {
		int colon = threadKey.indexOf(':');
		if (colon <= 0 || colon == threadKey.length() - 1) {
			return null; // bridge-mode key: no direct environment to read from
		}
		ChatSource.Env env;
		try {
			env = ChatScreens.defaultSource().env(threadKey.substring(0, colon));
		} catch (ChatSource.Unavailable e) {
			return null;
		}
		ThreadFeed feed = new ThreadFeed(env, threadKey.substring(colon + 1), () -> {
		});
		feed.start();
		return new MonitorFeeds.Feed() {
			@Override
			public ChatTranscript transcript() {
				return feed.transcript();
			}

			@Override
			public boolean live() {
				return feed.transcript().hasSnapshot();
			}

			@Override
			public void close() {
				feed.close();
			}
		};
	}

	void clearTracking() {
		visibility.clear();
		feeds.close();
	}

	@Override
	public void close() {
		loaded.clear();
		clearTracking();
	}
}
