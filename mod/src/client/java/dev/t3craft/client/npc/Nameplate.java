package dev.t3craft.client.npc;

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
		return of(info, ChipsConfig.current());
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

	/** Neighbourhood sign: project in bold, environment below. */
	public static Component sign(String groupLabel, String envLabel, int count) {
		return Component.literal(groupLabel).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE)
				.append(Component.literal("\n" + envLabel + " · " + count + (count == 1 ? " thread" : " threads")).withStyle(ChatFormatting.GRAY));
	}
}
