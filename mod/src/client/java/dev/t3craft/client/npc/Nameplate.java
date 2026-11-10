package dev.t3craft.client.npc;

import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.t3.Chips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Texts drawn around an NPC. Everything comes resolved in {@link NpcInfo} (colour, glyph, label,
 * chips); this only lays it out. The {@code *Text} helpers are pure so the layout is testable.
 */
public final class Nameplate {
	static final int CHIP_COLOR = 0x9AA4B2;

	private Nameplate() {
	}

	/** Vanilla name tag: the label (glyph + title) in the state colour, then the chips in grey. */
	public static Component of(NpcInfo info) {
		LabelConfig labels = LabelConfig.current();
		return labels.compact() ? compact(info, labels) : of(info, ChipsConfig.current());
	}

	/** Compact name tag (default): one short line, state glyph + truncated title, state colour. */
	public static Component compact(NpcInfo info, LabelConfig labels) {
		return Component.literal(NpcLabels.compactLine(info.state(), info.title(), labels.titleMax())).withColor(info.color());
	}

	public static Component of(NpcInfo info, ChipsConfig cfg) {
		MutableComponent c = Component.literal(info.label()).withColor(info.color());
		String chips = nameplateChips(info, cfg);
		if (chips != null) {
			c.append(Component.literal("  " + chips).withColor(CHIP_COLOR));
		}
		return c;
	}

	/** Floating panel above the name tag: environment · project, chips, then the current activity. */
	public static Component info(NpcInfo info) {
		return info(info, ChipsConfig.current());
	}

	/**
	 * The near panel in compact mode: environment · project, then the chips. No activity: the
	 * speech bubble shows it in the same radius, so it would be drawn twice.
	 */
	public static Component detail(NpcInfo info, ChipsConfig cfg) {
		MutableComponent c = Component.literal(info.sublabel()).withStyle(ChatFormatting.GRAY);
		String chips = panelChips(info, cfg);
		if (chips != null) {
			c.append(Component.literal("\n" + chips).withColor(CHIP_COLOR));
		}
		return c;
	}

	public static Component info(NpcInfo info, ChipsConfig cfg) {
		MutableComponent c = Component.literal(info.sublabel()).withStyle(ChatFormatting.GRAY);
		String chips = panelChips(info, cfg);
		if (chips != null) {
			c.append(Component.literal("\n" + chips).withColor(CHIP_COLOR));
		}
		if (info.activity() != null && !info.activity().isBlank()) {
			c.append(Component.literal("\n" + info.activity()).withStyle(ChatFormatting.ITALIC).withColor(0xD0D0D0));
		}
		return c;
	}

	/** Chips after the name tag label: {@code "[opus 5.5]"}, plus {@code "⎇ branch"} if enabled. */
	static @Nullable String nameplateChips(NpcInfo info, ChipsConfig cfg) {
		List<String> parts = new ArrayList<>(2);
		if (cfg.modelNameplate() && info.model() != null) {
			parts.add("[" + info.model() + "]");
		}
		if (cfg.branchNameplate() && info.branch() != null) {
			parts.add("⎇ " + info.branch());
		}
		return parts.isEmpty() ? null : String.join(" ", parts);
	}

	/** Panel chip line: {@code "◆ opus 5.5 · claude   ⎇ feat/x · wt-dir"}, or null when nothing to show. */
	static @Nullable String panelChips(NpcInfo info, ChipsConfig cfg) {
		List<String> parts = new ArrayList<>(2);
		String model = cfg.modelPanel() ? Chips.modelLine(info.model(), info.provider()) : null;
		if (model != null) {
			parts.add(model);
		}
		String branch = cfg.branchPanel() ? Chips.branchLine(info.branch(), info.worktree()) : null;
		if (branch != null) {
			parts.add(branch);
		}
		return parts.isEmpty() ? null : String.join("   ", parts);
	}

	/**
	 * World text pill (hologram style): the status dot in the state colour, then one activity line
	 * ({@link NpcLabels#pillLine}). Built only when the NPC's info changes (cached by text upstream).
	 */
	public static Component pill(NpcInfo info, ChatTheme theme) {
		int dot = WorldText.rgb(theme.color(NpcLabels.state(info.state())));
		return Component.literal(NpcLabels.DOT + " ").withColor(dot)
				.append(Component.literal(NpcLabels.pillLine(info.activity(), info.title())).withColor(WorldText.rgb(theme.text())));
	}

	/**
	 * The detail lines under an open pill (hologram style): the title (cut to {@code titleMax}), then
	 * environment · project and the chips, in the theme's muted colour.
	 */
	public static Component pillDetail(NpcInfo info, ChipsConfig cfg, ChatTheme theme, int titleMax) {
		String title = NpcLabels.truncate(info.title() == null ? "" : info.title().strip().replaceAll("\\s+", " "), titleMax);
		MutableComponent c = Component.literal(title).withColor(WorldText.rgb(theme.text()));
		if (info.sublabel() != null && !info.sublabel().isBlank()) {
			c.append(Component.literal("\n" + info.sublabel()).withColor(WorldText.rgb(theme.muted())));
		}
		String chips = panelChips(info, cfg);
		if (chips != null) {
			c.append(Component.literal("\n" + chips).withColor(WorldText.rgb(theme.muted())));
		}
		return c;
	}

	/** Neighbourhood sign: project in bold, environment below. */
	public static Component sign(String groupLabel, String envLabel, int count) {
		return Component.literal(groupLabel).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE)
				.append(Component.literal("\n" + envLabel + " · " + count + (count == 1 ? " thread" : " threads")).withStyle(ChatFormatting.GRAY));
	}
}
