package dev.t3craft.client.blocks;

import dev.t3craft.T3Craft;
import dev.t3craft.client.chat.ChatScreens;
import dev.t3craft.client.chat.EnvBadge;
import dev.t3craft.client.chat.ThreadDirectory;
import dev.t3craft.client.chat.ThreadList;
import dev.t3craft.client.chat.ThreadRow;
import dev.t3craft.client.queue.QueueClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The thread walls the client has drawn: one {@link WallBoard} per anchor, and the cards they all
 * show. Client thread only.
 *
 * <p><b>Data</b>: every {@value #REFRESH_TICKS} ticks, and only while some wall was drawn in the last
 * {@value #IDLE_TICKS} ticks, the cards are re-read from the chat's directory
 * ({@link ChatScreens#defaultDirectory()}: the shell views the client already keeps from
 * {@code subscribeShell}, read only; no stream of its own) plus the queued-run counts the queue
 * module already watches. Settled threads are left out. The list compares by value, so the boards
 * rebuild only when something they show changed.
 *
 * <p><b>Clicks</b>: {@link #hitAt} maps a block hit anywhere on a wall's cells to the card (or
 * "+N more" chip) under it.
 */
final class ThreadWalls {
	static final int REFRESH_TICKS = 10;
	static final int IDLE_TICKS = 100;
	/** Boards not drawn for this long are forgotten (chunk unloaded, block broken). */
	static final int PRUNE_TICKS = 600;

	/** A wall seen by the renderer: anchor, orientation and size this frame. */
	record Wall(BlockPos origin, Direction facing, int width, int height, WallBoard board) {
	}

	private final Supplier<ThreadDirectory> directory;
	private final Path configFile;
	private ThreadWallConfig config = ThreadWallConfig.DEFAULT;
	private List<WallCard> cards = List.of();
	private final Map<Long, Wall> walls = new HashMap<>();
	private int ticks;
	private int lastSeen = -IDLE_TICKS - 1;

	ThreadWalls(Supplier<ThreadDirectory> directory, Path configFile) {
		this.directory = directory;
		this.configFile = configFile;
		reloadConfig();
	}

	void reloadConfig() {
		config = ThreadWallConfig.load(configFile);
	}

	ThreadWallConfig config() {
		return config;
	}

	List<WallCard> cards() {
		return cards;
	}

	int size() {
		return walls.size();
	}

	void tick() {
		ticks++;
		if (ticks - lastSeen > IDLE_TICKS) {
			return; // no wall in view: read nothing
		}
		if (ticks % REFRESH_TICKS == 0 || cards.isEmpty() && ticks % 2 == 0) {
			refresh();
		}
		if (ticks % PRUNE_TICKS == 0) {
			walls.values().removeIf(w -> ticks - w.board().lastUsedTick > PRUNE_TICKS);
		}
	}

	private void refresh() {
		try {
			ThreadDirectory d = directory.get();
			Map<String, EnvBadge> badges = new HashMap<>();
			for (Map.Entry<String, ThreadList.EnvInfo> e : d.environments().entrySet()) {
				ThreadList.EnvInfo v = e.getValue();
				badges.put(e.getKey(), EnvBadge.of(e.getKey(), v.label(), v.color()));
			}
			List<WallCard> out = new ArrayList<>();
			for (ThreadRow r : d.rows()) {
				if (r.settled()) {
					continue;
				}
				EnvBadge b = badges.get(r.env());
				out.add(WallCard.of(r, b != null ? b : EnvBadge.of(r.env(), r.envLabel()), QueueClient.queued(r.key())));
			}
			if (!out.equals(cards)) {
				cards = List.copyOf(out);
			}
		} catch (RuntimeException e) {
			T3Craft.LOGGER.debug("T3Craft: thread wall refresh failed", e);
		}
	}

	/** The board of the wall anchored at {@code origin} (created on first use); marks it as drawn. */
	WallBoard board(BlockPos origin, Direction facing, int width, int height) {
		long k = origin.asLong();
		Wall w = walls.get(k);
		if (w == null || w.facing() != facing || w.width() != width || w.height() != height) {
			w = new Wall(origin.immutable(), facing, width, height, w == null ? new WallBoard() : w.board());
			walls.put(k, w);
		}
		if (ticks - lastSeen > IDLE_TICKS) {
			lastSeen = ticks;
			refresh(); // first wall in view: fresh data now, not in 10 ticks
		}
		lastSeen = ticks;
		w.board().lastUsedTick = ticks;
		return w.board();
	}

	/** The card or chip under a block hit ({@code pos}, the hit face, the exact point), or null. */
	WallLayout.@Nullable Hit hitAt(BlockPos pos, Direction face, Vec3 at) {
		for (Wall w : walls.values()) {
			if (face != w.facing() || !WallGeometry.onWall(w.origin(), w.facing(), w.width(), w.height(), pos)) {
				continue;
			}
			if (Math.abs(WallGeometry.offPlane(w.origin(), w.facing(), at.x, at.z)) > WallGeometry.PLANE_TOLERANCE) {
				continue;
			}
			float[] p = WallGeometry.toBoard(w.origin(), w.facing(), w.height(), ThreadBoardRenderer.PPB, at.x, at.y, at.z);
			WallLayout.Hit h = w.board().hit(p[0], p[1]);
			if (h != null) {
				return h;
			}
		}
		return null;
	}

	void clear() {
		walls.clear();
		cards = List.of();
		lastSeen = -IDLE_TICKS - 1;
	}
}
