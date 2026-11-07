package dev.t3craft.client.queue;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ShellState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Queued runs per thread, every environment, in T3's delivery order. Reading needs no operate
 * session; the ▲ ▼ (reorder) and ✕ (cancel, with confirmation) buttons are
 * {@link OperateUi#guard guarded}: disabled with "Requires control (operate)" when read-only.
 * Watches the focused thread plus the working threads ({@link QueueTargets#SCREEN_CAP}) while open.
 */
public final class QueueScreen extends Screen {
	private static final int ROW = 22;
	private static final int BTN = 20;

	private final @Nullable Screen parent;
	private final QueueStore.@Nullable Key focus;
	private final List<QueueStore.Watch> watches = new ArrayList<>();
	private final Set<QueueStore.Key> watched = new LinkedHashSet<>();
	private List<Row> rows = List.of();
	private String signature = "";
	private int page;
	private @Nullable Component status;
	private boolean busy;

	/** A thread header ({@code run == null}) or one queued run. */
	private record Row(QueueStore.Snapshot thread, @Nullable QueuedRun run, int index) {
	}

	public QueueScreen(@Nullable Screen parent, QueueStore.@Nullable Key focus) {
		super(Component.translatable("t3craft.queue.title"));
		this.parent = parent;
		this.focus = focus;
	}

	private Map<String, ShellState.View> views() {
		T3CraftClient c = T3CraftClient.get();
		return c == null ? Map.of() : QueueClient.views(c);
	}

	private void watchTargets() {
		Set<QueueStore.Key> want = new LinkedHashSet<>();
		if (focus != null) {
			want.add(focus);
		}
		want.addAll(QueueTargets.working(views(), QueueTargets.SCREEN_CAP));
		for (QueueStore.Key k : want) {
			if (watched.add(k)) {
				watches.add(QueueClient.store().watch(k.envId(), k.threadId()));
			}
		}
	}

	private List<Row> buildRows() {
		List<Row> out = new ArrayList<>();
		for (QueueStore.Snapshot s : QueueClient.store().all()) {
			if (!watched.contains(s.key())) {
				continue;
			}
			// Only threads with something to show: a queue, or the focused one (even if empty/unknown).
			if (s.runs().isEmpty() && !s.key().equals(focus)) {
				continue;
			}
			out.add(new Row(s, null, 0));
			int i = 0;
			for (QueuedRun r : s.runs()) {
				out.add(new Row(s, r, ++i));
			}
		}
		return out;
	}

	private static String signature(List<Row> rows) {
		StringBuilder b = new StringBuilder();
		for (Row r : rows) {
			b.append(r.thread().key()).append('/').append(r.thread().status()).append('/')
					.append(r.run() == null ? "-" : r.run().id() + r.run().held()).append(';');
		}
		return b.toString();
	}

	private int perPage() {
		return Math.max(1, (height - 36 - 70) / ROW);
	}

	private int listWidth() {
		return Math.min(480, width - 32);
	}

	@Override
	protected void init() {
		if (watches.isEmpty() && QueueClient.readable()) {
			watchTargets();
		}
		rows = buildRows();
		signature = signature(rows);
		int pages = Math.max(1, (rows.size() + perPage() - 1) / perPage());
		page = Math.min(page, pages - 1);
		int w = listWidth();
		int x = width / 2 - w / 2;
		int from = page * perPage();
		for (int i = from; i < Math.min(rows.size(), from + perPage()); i++) {
			Row row = rows.get(i);
			QueuedRun r = row.run();
			if (r == null) {
				continue;
			}
			int ry = 36 + (i - from) * ROW;
			int bx = x + w - 3 * (BTN + 2);
			String title = QueueTargets.title(views(), r.envId(), r.threadId());
			List<QueuedRun> q = row.thread().runs();
			OperateAction up = QueueClient.ops().moveAction(q, r, QueueOps.Direction.UP, title);
			OperateAction down = QueueClient.ops().moveAction(q, r, QueueOps.Direction.DOWN, title);
			addButton(Component.literal("▲"), bx, ry, r.envId(), up, "t3craft.queue.up");
			addButton(Component.literal("▼"), bx + BTN + 2, ry, r.envId(), down, "t3craft.queue.down");
			addButton(Component.literal("✕").withStyle(ChatFormatting.RED), bx + 2 * (BTN + 2), ry, r.envId(),
					QueueClient.ops().cancelAction(r, title), "t3craft.queue.cancel");
		}

		int by = height - 28;
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(width / 2 - 104, by, 20, 20).build());
		prev.active = page > 0;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 80, by, 160, 20).build());
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(width / 2 + 84, by, 20, 20).build());
		next.active = page < pages - 1;
	}

	private void addButton(Component label, int x, int y, String envId, @Nullable OperateAction action, String tooltipKey) {
		Button b = addRenderableWidget(Button.builder(label, btn -> {
			if (action == null || busy) {
				return;
			}
			busy = true;
			status = Component.translatable("t3craft.queue.sending").withStyle(ChatFormatting.GRAY);
			OperateUi.run(action, OperateGate.Origin.UI, this, r -> {
				busy = false;
				status = resultLine(r);
			});
		}).bounds(x, y, BTN, BTN).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(tooltipKey))).build());
		if (OperateUi.guard(b, envId)) {
			b.active = action != null;
		}
	}

	@Override
	public void tick() {
		if (!busy && !signature.equals(signature(buildRows()))) {
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
		int w = listWidth();
		int x = width / 2 - w / 2;
		if (!QueueClient.readable()) {
			g.centeredText(font, Component.translatable("t3craft.queue.unavailable", QueueClient.source().unavailableReason())
					.withStyle(ChatFormatting.YELLOW), width / 2, 50, 0xFFFFFFFF);
		} else if (rows.isEmpty()) {
			g.centeredText(font, Component.translatable(watched.isEmpty() ? "t3craft.queue.none_working" : "t3craft.queue.none")
					.withStyle(ChatFormatting.GRAY), width / 2, 50, 0xFFFFFFFF);
		}
		int from = page * perPage();
		for (int i = from; i < Math.min(rows.size(), from + perPage()); i++) {
			Row row = rows.get(i);
			int ry = 36 + (i - from) * ROW;
			if (row.run() == null) {
				g.text(font, header(row.thread()), x, ry + 6, 0xFFFFFFFF);
			} else {
				int textW = w - 3 * (BTN + 2) - 8;
				Component line = runLine(row);
				if (font.width(line) <= textW) {
					g.text(font, line, x + 8, ry + 6, 0xFFFFFFFF);
				} else {
					g.text(font, font.substrByWidth(line, textW - font.width("…")).getString() + "…", x + 8, ry + 6, 0xFFDDDDDD);
				}
			}
		}
		if (status != null) {
			g.centeredText(font, status, width / 2, height - 42, 0xFFFFFFFF);
		}
	}

	private Component header(QueueStore.Snapshot s) {
		String env = OperateClient.label(s.key().envId());
		String title = QueueTargets.title(views(), s.key().envId(), s.key().threadId());
		MutableComponent c = Component.literal(env).withStyle(ChatFormatting.AQUA)
				.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(title == null ? s.key().threadId() : title).withStyle(ChatFormatting.WHITE));
		Component state = switch (s.status()) {
			case OK -> Component.translatable("t3craft.queue.badge", s.runs().size()).withStyle(ChatFormatting.GOLD);
			case PAUSED -> Component.translatable("t3craft.queue.paused").withStyle(ChatFormatting.GRAY);
			case LOADING -> Component.translatable("t3craft.queue.loading").withStyle(ChatFormatting.GRAY);
			case UNAVAILABLE -> Component.translatable("t3craft.queue.unknown_short").withStyle(ChatFormatting.RED);
		};
		return c.append(Component.literal("  ")).append(state);
	}

	private static Component runLine(Row row) {
		QueuedRun r = row.run();
		MutableComponent c = Component.literal(row.index() + ". ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(r.preview() == null ? r.id() : r.preview()).withStyle(ChatFormatting.WHITE));
		if (r.held()) {
			c.append(Component.literal("  ")).append(Component.translatable("t3craft.queue.held").withStyle(ChatFormatting.GOLD));
		}
		if (r.automatic()) {
			c.append(Component.literal("  ")).append(Component.translatable("t3craft.queue.automatic").withStyle(ChatFormatting.DARK_AQUA));
		}
		return c;
	}

	/** Player-facing result line (also used by the command). */
	static Component resultLine(OperateResult r) {
		ChatFormatting color = switch (r.outcome()) {
			case OK -> ChatFormatting.GREEN;
			case CANCELLED, READ_ONLY -> ChatFormatting.GRAY;
			case FAILED -> ChatFormatting.GOLD;
			default -> ChatFormatting.RED;
		};
		return Component.translatable("t3craft.queue.result." + r.outcome().wire, r.message()).withStyle(color);
	}

	@Override
	public void removed() {
		watches.forEach(QueueStore.Watch::close);
		watches.clear();
		watched.clear();
		super.removed();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
