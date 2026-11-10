package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.blocks.LampOptions;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.workstation.StationStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The config page of the status lamps (desk lamp, board bulb, hub beacon lamp) and of the HQ kit's paper lantern, which
 * share the lamp block entity and its renderer (issue #43). The binding picker offers a thread (a desk lamp), a board
 * slot (the n-th thread that needs the player) or the whole HQ (the beacon); the options are {@link LampOptions}: how
 * strong the glow is, whether it breathes with the state and whether the lamp gives off motes. The block state (model
 * and light) is set by the status and is not an option.
 */
public final class LampConfigSection implements BlockConfigSection {
	private static final List<Binding.Kind> KINDS = List.of(Binding.Kind.THREAD, Binding.Kind.BOARD, Binding.Kind.BEACON);

	private final String blockName;

	public LampConfigSection(String blockName) {
		this.blockName = blockName;
	}

	@Override
	public String blockName() {
		return blockName;
	}

	@Override
	public List<Binding.Kind> kinds() {
		return KINDS;
	}

	@Override
	public String kindTitle() {
		return "Shows";
	}

	@Override
	public String kindLabel(Binding.Kind kind) {
		return switch (kind) {
			case THREAD -> "Desk lamp";
			case BOARD -> "Board bulb";
			case BEACON -> "Hub beacon";
			default -> BindingChoices.kindLabel(kind);
		};
	}

	@Override
	public void options(BlockConfigPanel.Form form, BlockConfig config) {
		LampOptions o = LampOptions.of(config.options());
		form.select("Glow", Arrays.asList(LampOptions.Glow.values()), g -> g.label, o.glow(),
				g -> form.set(LampOptions.GLOW, g.id, LampOptions.Glow.NORMAL.id));
		form.toggle("Breathe with the state", o.pulse(), v -> form.set(LampOptions.PULSE, Boolean.toString(v), "true"));
		form.toggle("Dust motes when it asks for you", o.motes(), v -> form.set(LampOptions.MOTES, Boolean.toString(v), "true"));
		form.note("Only the glow over the lamp changes. Its model and light level always follow the thread's state.");
	}

	@Override
	public BlockPreview.Model preview(BlockConfig config, BindingChoices choices, ChatTheme t) {
		LampOptions o = LampOptions.of(config.options());
		Binding b = config.binding();
		StationStatus st = null;
		String title;
		String detail;
		switch (b.kind()) {
			case THREAD -> {
				BindingChoices.Choice c = choices.find(b);
				st = c.status() == StationStatus.ARCHIVED ? null : c.status();
				title = c.label();
				detail = st == null ? "Archived thread: the lamp is off." : "Shows this thread's state.";
			}
			case BOARD -> {
				List<BindingChoices.Choice> urgent = urgent(choices);
				int i = b.boardIndex();
				if (i >= 0 && i < urgent.size()) {
					st = urgent.get(i).status();
					title = "Slot " + (i + 1) + ": " + urgent.get(i).label();
					detail = "Shows the thread number " + (i + 1) + " that needs you (errors first).";
				} else {
					title = "Slot " + (Math.max(0, i) + 1) + ": nobody waiting";
					detail = "No thread needs you for this slot: the bulb is off.";
				}
			}
			case BEACON -> {
				st = beacon(choices);
				title = "Whole HQ";
				detail = "Shows the most urgent state of every thread.";
			}
			default -> {
				title = "Not bound";
				detail = "An unbound lamp stays off.";
			}
		}
		List<BlockPreview.Line> lines = new ArrayList<>();
		lines.add(new BlockPreview.Line(detail, t.codeText()));
		String glow = o.glow() == LampOptions.Glow.OFF ? "off" : o.glow().label.toLowerCase(java.util.Locale.ROOT) + (o.pulse() ? ", breathing" : ", steady");
		lines.add(new BlockPreview.Line("Glow: " + glow, t.muted()));
		lines.add(new BlockPreview.Line("Motes: " + (o.motes() ? "on" : "off"), t.muted()));
		return new BlockPreview.Model(title, st == null ? "OFF" : st.word, st == null ? t.disabled() : MonitorConfigSection.color(st, t), lines, 1f);
	}

	/** The threads that need the player, errors first, then "needs you" (ties by key): board bulb {@code i} shows the i-th. */
	static List<BindingChoices.Choice> urgent(BindingChoices choices) {
		List<BindingChoices.Choice> out = new ArrayList<>();
		for (BindingChoices.Choice c : choices.threads()) {
			if (c.status() == StationStatus.ERROR || c.status() == StationStatus.ATTENTION) {
				out.add(c);
			}
		}
		out.sort(Comparator.comparingInt((BindingChoices.Choice c) -> c.status() == StationStatus.ERROR ? 0 : 1).thenComparing(c -> c.binding().value()));
		return out;
	}

	/** The beacon's state: needs you, then error, then working, else idle (settled threads do not count). */
	static StationStatus beacon(BindingChoices choices) {
		boolean error = false;
		boolean working = false;
		for (BindingChoices.Choice c : choices.threads()) {
			switch (c.status()) {
				case ATTENTION -> {
					return StationStatus.ATTENTION;
				}
				case ERROR -> error = true;
				case WORKING -> working = true;
				default -> {
				}
			}
		}
		return error ? StationStatus.ERROR : working ? StationStatus.WORKING : StationStatus.IDLE;
	}
}
