package dev.t3craft.client.queue;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.t3.DirectController;
import dev.t3craft.t3.ShellState;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Client wiring of the run queue (inventario item 24): {@code /t3craft queue} and
 * {@code /t3craft operate queue …} ({@link QueueCommands}), the {@link QueueScreen}, and the
 * "n queued" line on NPC panels. One call from {@code T3CraftClient}.
 *
 * <p>Reading needs a thread stream ({@link QueueSource}); the shell carries no runs. The source is
 * {@link StreamQueueSource}: the connection's shared thread streams, no request path of its own.
 * When a thread cannot be read the views say "unavailable", never "empty". Writes go only
 * through {@link OperateUi} / {@code OperateGate}.
 */
public final class QueueClient {
	private static final int BACKGROUND_EVERY_TICKS = 40;

	private static volatile QueueSource source = QueueSource.UNAVAILABLE;
	private static final QueueStore STORE = new QueueStore(() -> source, () -> {
	});
	private static @Nullable QueueOps ops;
	private static int ticks;
	private static volatile @Nullable Runnable pending;

	private QueueClient() {
	}

	public static void register(T3CraftClient client) {
		install(new StreamQueueSource(client::direct)); // shared T3Connection thread streams (feat/chat)
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> QueueCommands.build(dispatcher, client));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Runnable r = pending;
			if (r != null) {
				pending = null;
				r.run();
			}
			if (++ticks % BACKGROUND_EVERY_TICKS == 0) {
				refreshBackground(client);
			}
		});
	}

	/** Runs {@code r} on the next client tick (a chat command closes its screen after it returns). */
	public static void nextTick(Runnable r) {
		pending = r;
	}

	/** Plugs in where thread streams come from (e.g. {@code QueueSource} over T3Connection.subscribeThread). */
	public static void install(QueueSource s) {
		source = s == null ? QueueSource.UNAVAILABLE : s;
		STORE.clear();
	}

	public static QueueSource source() {
		return source;
	}

	public static boolean readable() {
		return source != QueueSource.UNAVAILABLE;
	}

	public static QueueStore store() {
		return STORE;
	}

	public static synchronized QueueOps ops() {
		if (ops == null) {
			ops = new QueueOps(OperateUi.gate(), QueueActions.create());
		}
		return ops;
	}

	public static Map<String, ShellState.View> views(T3CraftClient client) {
		DirectController d = client.direct();
		return d == null ? Map.of() : d.shellViews();
	}

	/** Keeps the working threads watched (capped) so their NPC panels can show the badge. */
	private static void refreshBackground(T3CraftClient client) {
		if (!readable() || client.direct() == null) {
			STORE.setBackground(Set.of());
			return;
		}
		STORE.setBackground(new LinkedHashSet<>(QueueTargets.working(views(client), QueueTargets.BADGE_CAP)));
	}

	/**
	 * Queued runs of an NPC id ({@code env:thread}) as far as the client watches them (the working
	 * threads' background watches, or an open queue screen); 0 when none or unknown. Cheap: a map
	 * lookup, no subscription of its own (thread wall cards).
	 */
	public static int queued(String npcId) {
		QueueStore.Key k = QueueStore.Key.parse(npcId);
		return STORE.count(k.envId(), k.threadId());
	}

	/** "⏳ n queued" for an NPC id ({@code env:thread}), or null when empty or unknown. */
	public static @Nullable Component badge(String npcId) {
		QueueStore.Key k = QueueStore.Key.parse(npcId);
		int n = STORE.count(k.envId(), k.threadId());
		return n > 0 ? Component.translatable("t3craft.queue.badge", n).withStyle(ChatFormatting.GOLD) : null;
	}

	/**
	 * Panel hook that keeps another one's line (feat/npc-helpers) and adds the queue badge:
	 * {@code npcs.setPanelExtra(QueueClient.withBadge(helpers::panelExtra))}.
	 */
	public static Function<String, @Nullable Component> withBadge(Function<String, @Nullable Component> other) {
		return id -> {
			Component a = other == null ? null : other.apply(id);
			Component b = badge(id);
			if (a == null) {
				return b;
			}
			if (b == null) {
				return a;
			}
			MutableComponent out = a.copy();
			return out.append(Component.literal("  ")).append(b);
		};
	}
}
