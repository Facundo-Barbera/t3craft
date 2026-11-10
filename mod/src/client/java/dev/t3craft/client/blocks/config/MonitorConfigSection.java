package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.MonitorBlock;
import dev.t3craft.client.blocks.BlockAccent;
import dev.t3craft.client.blocks.LogPreview;
import dev.t3craft.client.blocks.MonitorLog;
import dev.t3craft.client.blocks.MonitorOptions;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.workstation.StationStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * The monitor's config page (monitors and the large screens they merge into): the content is the binding's kind
 * (log = a thread, project feed, environment feed, status = the HQ beacon), then the target, and the options of
 * {@link MonitorOptions}: text size, accent, rows shown, age in the status chip. A wall is edited as one: the page
 * writes every cell of the screen ({@link MonitorBlock#extent}).
 */
public final class MonitorConfigSection implements BlockConfigSection {
	private static final List<Binding.Kind> KINDS = List.of(Binding.Kind.THREAD, Binding.Kind.PROJECT, Binding.Kind.ENV, Binding.Kind.BEACON);
	/** Sample tool rows of a log preview (schematic: the real rows are the thread's last tool calls). */
	private static final List<String> SAMPLE_LOG = List.of("Read  src/main/App.java", "Edit  src/main/App.java  +12 -3", "Bash  ./gradlew build",
			"Grep  \"binding\"", "Edit  docs/features/blocks.md  +8 -1", "Read  README.md");

	/** Most rows a log preview takes from a live thread. */
	private static final int PREVIEW_ROWS = 8;

	/** The newest real rows of a thread's log, oldest first (empty = none available; the preview then shows sample rows). */
	@FunctionalInterface
	public interface LogRows {
		List<MonitorLog.Row> latest(String threadKey, int max);
	}

	private final LogRows realRows;

	/** The page of the game: real rows from the log feeds the client has open ({@link LogPreview}). */
	public MonitorConfigSection() {
		this(LogPreview::latest);
	}

	public MonitorConfigSection(LogRows realRows) {
		this.realRows = realRows;
	}

	@Override
	public String blockName() {
		return "Monitor";
	}

	@Override
	public List<Binding.Kind> kinds() {
		return KINDS;
	}

	@Override
	public String kindTitle() {
		return "Content";
	}

	@Override
	public String kindLabel(Binding.Kind kind) {
		return switch (kind) {
			case THREAD -> "Log (one thread)";
			case PROJECT -> "Project feed";
			case ENV -> "Environment feed";
			case BEACON -> "Status (whole HQ)";
			default -> BindingChoices.kindLabel(kind);
		};
	}

	@Override
	public List<BlockPos> cells(BlockGetter level, BlockPos pos) {
		return MonitorBlock.extent(level, pos, level.getBlockState(pos)).cells();
	}

	// ---- options ---------------------------------------------------------------------------------------------------

	@Override
	public void options(BlockConfigPanel.Form form, BlockConfig config) {
		MonitorOptions o = MonitorOptions.of(config.options());
		boolean status = config.binding().kind() == Binding.Kind.BEACON;
		form.select("Text size", Arrays.asList(MonitorOptions.TextSize.values()), t -> t.label, o.textSize(),
				t -> form.set(MonitorOptions.TEXT_SIZE, t.id, MonitorOptions.TextSize.NORMAL.id));
		form.select("Accent", MonitorOptions.ACCENTS, a -> BlockAccent.label(a, status ? "Default" : "Status colour"), o.accent(),
				a -> form.set(MonitorOptions.ACCENT, a, MonitorOptions.ACCENT_STATUS));
		if (status) {
			// the status screen is not a log: no rows to cap, no status chip to age
			form.note("Text size scales the numbers and labels. The accent colours the PRS TO REVIEW and ACTIVE PROJECTS cells; the tiles keep their state colours.");
			return;
		}
		List<Integer> rows = new ArrayList<>(MonitorOptions.ROW_CHOICES);
		if (!rows.contains(o.rows())) {
			rows.add(o.rows()); // a value written by hand stays selectable
		}
		form.select("Rows shown", rows, n -> n == 0 ? "As many as fit" : Integer.toString(n), o.rows(),
				n -> form.set(MonitorOptions.ROWS, Integer.toString(n), "0"));
		form.toggle("Show the age in the status chip", o.showAge(), v -> form.set(MonitorOptions.SHOW_AGE, Boolean.toString(v), "true"));
		form.note("Text size changes the near view only; from afar the screen always shows its status in big letters.");
	}

	// ---- preview ---------------------------------------------------------------------------------------------------

	@Override
	public BlockPreview.Model preview(BlockConfig config, BindingChoices choices, ChatTheme t) {
		MonitorOptions o = MonitorOptions.of(config.options());
		Binding b = config.binding();
		int rows = o.rows() > 0 ? o.rows() : Integer.MAX_VALUE;
		float scale = 1f / o.textSize().density;
		int fixed = o.accentColor(t);
		List<BlockPreview.Line> lines = new ArrayList<>();
		return switch (b.kind()) {
			case THREAD -> {
				BindingChoices.Choice c = choices.find(b);
				StationStatus st = c.status();
				String chip = o.showAge() ? st.word + " - 3m" : st.word;
				// the thread's real newest rows when its log is open on the client, sample rows otherwise (T10)
				List<MonitorLog.Row> real = realRows.latest(b.value(), Math.min(rows, PREVIEW_ROWS));
				for (MonitorLog.Row r : real) {
					if (lines.size() >= rows) {
						break;
					}
					lines.add(new BlockPreview.Line(r.name().isEmpty() ? r.text() : r.name() + "  " + r.text(), 0xFF000000 | r.color()));
				}
				for (int i = 0; real.isEmpty() && i < SAMPLE_LOG.size() && lines.size() < rows; i++) {
					lines.add(new BlockPreview.Line(SAMPLE_LOG.get(i), t.codeText()));
				}
				yield new BlockPreview.Model(c.label(), chip, fixed != 0 ? fixed : color(st, t), lines, scale);
			}
			case PROJECT, ENV -> {
				BindingChoices.Choice c = choices.find(b);
				List<BindingChoices.Choice> active = new ArrayList<>();
				for (BindingChoices.Choice th : choices.threads()) {
					boolean in = b.kind() == Binding.Kind.PROJECT ? b.value().equals(th.project()) : b.value().equals(th.env());
					if (in && th.status() != StationStatus.ARCHIVED) {
						active.add(th);
					}
				}
				active.sort((x, y) -> Integer.compare(urgency(x.status()), urgency(y.status())));
				for (BindingChoices.Choice th : active) {
					if (lines.size() >= rows) {
						break;
					}
					lines.add(new BlockPreview.Line(th.status().word + "  " + th.label(), color(th.status(), t)));
				}
				if (active.isEmpty()) {
					lines.add(new BlockPreview.Line(b.kind() == Binding.Kind.ENV ? "No threads in this environment" : "No active threads", t.muted()));
				}
				String title = c.label() + " - " + active.size() + (active.size() == 1 ? " active thread" : " active threads");
				StationStatus top = active.isEmpty() ? StationStatus.IDLE : active.getFirst().status();
				yield new BlockPreview.Model(title, "", fixed != 0 ? fixed : color(top, t), lines, scale);
			}
			case BEACON -> {
				int[] n = new int[StationStatus.values().length];
				for (BindingChoices.Choice th : choices.threads()) {
					n[th.status().ordinal()]++;
				}
				for (StationStatus s : new StationStatus[] {StationStatus.ATTENTION, StationStatus.ERROR, StationStatus.WORKING, StationStatus.DONE,
						StationStatus.IDLE}) {
					lines.add(new BlockPreview.Line(s.word + ": " + n[s.ordinal()], color(s, t)));
				}
				yield new BlockPreview.Model("Workspace status", "", fixed != 0 ? fixed : t.accent(), lines, scale);
			}
			default -> {
				lines.add(new BlockPreview.Line("The screen stays dark (standby) until it is bound.", t.muted()));
				yield new BlockPreview.Model("NO THREAD", "", t.disabled(), lines, 1f);
			}
		};
	}

	/** The theme token of a status (the preview draws with the UI theme, not the in-world screen palette). */
	static int color(StationStatus s, ChatTheme t) {
		return switch (s) {
			case ATTENTION -> t.needsYou();
			case ERROR -> t.error();
			case WORKING -> t.working();
			case DONE -> t.done();
			case IDLE, ARCHIVED -> t.idle();
		};
	}

	private static int urgency(StationStatus s) {
		return switch (s) {
			case ATTENTION -> 0;
			case ERROR -> 1;
			case WORKING -> 2;
			case DONE -> 3;
			case IDLE -> 4;
			case ARCHIVED -> 5;
		};
	}
}
