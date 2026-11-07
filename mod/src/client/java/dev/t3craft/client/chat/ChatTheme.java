// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
// warmStudio() copies the "Warm Studio" tokens of AgentCraft's UiStyle.java, gui/ui-style.json,
// palette.json and assets-src/ui-style.md (paper GUI, status palette, metrics).
package dev.t3craft.client.chat;

import dev.t3craft.t3.ThreadState;
import org.jspecify.annotations.Nullable;

/**
 * Colours, metrics and (optionally) the sprite kit of the chat components ({@link ThreadListView},
 * {@link ConversationPanel}, {@link ThreadBrowser}). An immutable value: hand one to a component to
 * restyle it; nothing reads a global. Pure Java (no Minecraft classes).
 *
 * <ul>
 *   <li>{@link #warmStudio()} (default): AgentCraft's console, token for token. Cream paper panels
 *   drawn with AgentCraft's nine-slice kit sprites (copied to {@code t3craft:chat/*}), ink text with
 *   no shadow, brass prompt and emphasis, clay for "you" and for what waits on you.</li>
 *   <li>{@link #terminal()}: the portable terminal's dark teal palette, flat fills (no sprites), so
 *   the components sit inside the portable's own frame.</li>
 * </ul>
 * Derive variants with {@link #toBuilder()}:
 * <pre>{@code
 * ChatTheme mine = ChatTheme.terminal().toBuilder().accent(0xFFFFAA00).sprites(null).build();
 * }</pre>
 *
 * @param sprites    sprite path prefix ({@code "t3craft:chat/"}) for the nine-slice kit
 *                   (panel_paper, panel_inset, pill, keycap, text_field, scroll_*, dot_*, icon_*,
 *                   bubble), or null to draw everything with flat fills of the colours below
 * @param backdrop   full-screen veil behind a screen host (top); {@code backdropBottom}: gradient end
 * @param panel      panel surface (paper)
 * @param panelHi    raised highlight (agent avatar, focused field)
 * @param inset      recessed well (feed)
 * @param edge       1 px rules and borders
 * @param accent     titles and the selection bar
 * @param brass      prompt prefix, focus ring, "thinking"
 * @param text       body text (ink)
 * @param muted      secondary text
 * @param disabled   separators, disabled text
 * @param ghost      placeholder text in the field
 * @param selected   selected row fill
 * @param hover      hovered row fill
 * @param you        stripe/avatar of the user's lines (clay)
 * @param youText    "You" lead text (clay dark)
 * @param agent      stripe/avatar of the agent's lines
 * @param agentText  agent name lead text
 * @param tool       tool name on a tool line
 * @param toolArg    tool argument on a tool line
 * @param result     tool results / success
 * @param errorText  errors in the feed
 * @param codeBg     code block background
 * @param codeText   code block text
 * @param inlineCode inline code and paths
 * @param link       links
 * @param addFg      diff "+" text; {@code addBg} its row tint
 * @param delFg      diff "-" text; {@code delBg} its row tint
 * @param keycapText text on key caps and chips
 * @param needsYou   status colours: waiting on you … idle (AgentCraft status families)
 * @param shadow     draw text with the vanilla shadow
 * @param row        feed / list text row height (AgentCraft: 10)
 * @param toolRow    tool line height (AgentCraft: 13, for the 12 px icon)
 * @param listRow    thread list row height
 * @param margin     outer margin (AgentCraft: 8)
 * @param gap        gap between panels (AgentCraft: 4)
 * @param pad        panel content padding (AgentCraft panel_paper: 8)
 */
public record ChatTheme(
		@Nullable String sprites,
		int backdrop,
		int backdropBottom,
		int panel,
		int panelHi,
		int inset,
		int edge,
		int accent,
		int brass,
		int text,
		int muted,
		int disabled,
		int ghost,
		int selected,
		int hover,
		int you,
		int youText,
		int agent,
		int agentText,
		int tool,
		int toolArg,
		int result,
		int errorText,
		int codeBg,
		int codeText,
		int inlineCode,
		int link,
		int addFg,
		int addBg,
		int delFg,
		int delBg,
		int keycapText,
		int needsYou,
		int error,
		int pending,
		int working,
		int done,
		int idle,
		boolean shadow,
		int row,
		int toolRow,
		int listRow,
		int margin,
		int gap,
		int pad) {

	public static final String KIT = "t3craft:chat/";

	/** AgentCraft "Warm Studio" paper console (default look). */
	public static ChatTheme warmStudio() {
		return new ChatTheme(
				KIT,
				0x5C3B2A20, // walnut @ 92 (console veil)
				0x781F1E1D, // ink @ 120 (bottom gradient)
				0xFFF4EFE6, // palette.ui.panel
				0xFFFFFBF4, // palette.ui.panel_hi
				0xFFE9E1D3, // palette.ui.inset
				0xFFC9BBA3, // palette.ui.edge
				0xFFD97757, // clay: selection bar (AgentCraft popup selection)
				0xFFC9A227, // brass
				0xFF1F1E1D, // paper.text (ink)
				0xFF655E55, // paper.muted
				0xFFA39B8E, // paper.disabled
				0xFFBCAD95, // ink_ui.ghost_on_paper
				0x2ED97757, // clay @ 46 (popup selected row)
				0xFFE3DACB, // palette.ui.panel_shade
				0xFFD97757, // clay (user stripe)
				0xFFB4553A, // clay dark ("You")
				0xFF3C74B3, // identity "kit" (agent stripe)
				0xFF366294, // kit text_on_light (agent name)
				0xFF624E16, // monitor.tool
				0xFF1F1E1D, // monitor.tool_arg
				0xFF485A48, // monitor.result
				0xFF9A2F2B, // monitor.error
				0xFF26221F, // glass (code)
				0xFFEDE5D7, // monitor_dark.text
				0xFF6C5415, // paper.path
				0xFF874431, // paper.link
				0xFF455746, // paper.add_fg
				0xFFD6DDCC, // paper.add_bg
				0xFF873C2A, // paper.del_fg
				0xFFF2D5C4, // paper.del_bg
				0xFF34312E, // palette.ui.text (keycap)
				0xFFD97757, // status.waiting
				0xFFC2413B, // status.error
				0xFFC9A227, // status.thinking
				0xFF2FA3A0, // status.working
				0xFF8FA98B, // status.done
				0xFF9C9488, // status.idle
				false,
				10,
				13,
				13,
				8,
				4,
				8);
	}

	/** The portable terminal's dark teal palette, flat fills (no kit sprites). */
	public static ChatTheme terminal() {
		return new ChatTheme(
				null,
				0x88000000,
				0x88000000,
				0xFF1A2027,
				0xFF27414A,
				0xFF12171C,
				0xFF3A6B73,
				0xFF55FFFF,
				0xFFFFD27F,
				0xFFE6E6E6,
				0xFF8A949E,
				0xFF4A5A64,
				0xFF5A6670,
				0xFF27414A,
				0xFF1F2A31,
				0xFF7FD4FF,
				0xFF7FD4FF,
				0xFF55FFFF,
				0xFFB6F09C,
				0xFFFFD27F,
				0xFFE6E6E6,
				0xFFB8CBB3,
				0xFFFF8888,
				0xFF0B0F12,
				0xFFD0D6DC,
				0xFFFFE08A,
				0xFF6FD3FF,
				0xFFB8CBB3,
				0xFF1E3326,
				0xFFF4A585,
				0xFF3A2320,
				0xFFE6E6E6,
				0xFFFFAA00,
				0xFFFF5555,
				0xFFFFE08A,
				0xFF55FFFF,
				0xFF55FF55,
				0xFFAAAAAA,
				false,
				10,
				12,
				12,
				4,
				3,
				4);
	}

	/** Colour of a thread's attention level. */
	public int color(Attention a) {
		return switch (a) {
			case NEEDS_YOU -> needsYou;
			case ERROR -> error;
			case PENDING -> pending;
			case WORKING -> working;
			case DONE -> done;
			case IDLE -> idle;
		};
	}

	/** Colour of a thread state. */
	public int color(ThreadState s) {
		return switch (s) {
			case NEEDS_ATTENTION -> needsYou;
			case ERROR -> error;
			case WORKING -> working;
			case DONE -> done;
			case IDLE -> idle;
		};
	}

	/** AgentCraft status family of an attention level (dot sprite names: dot_&lt;family&gt;). */
	public static String family(Attention a) {
		return switch (a) {
			case NEEDS_YOU -> "waiting";
			case ERROR -> "error";
			case PENDING -> "thinking";
			case WORKING -> "working";
			case DONE -> "done";
			case IDLE -> "idle";
		};
	}

	public static String family(ThreadState s) {
		return switch (s) {
			case NEEDS_ATTENTION -> "waiting";
			case ERROR -> "error";
			case WORKING -> "working";
			case DONE -> "done";
			case IDLE -> "idle";
		};
	}

	public boolean hasSprites() {
		return sprites != null;
	}

	/** {@code argb} with its alpha replaced (0..255). */
	public static int withAlpha(int argb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
	}

	public Builder toBuilder() {
		return new Builder(this);
	}

	/** Mutable copy of a theme; {@link #build()} returns a new immutable one. */
	public static final class Builder {
		private @Nullable String sprites;
		private int backdrop, backdropBottom, panel, panelHi, inset, edge, accent, brass, text, muted, disabled, ghost, selected, hover;
		private int you, youText, agent, agentText, tool, toolArg, result, errorText, codeBg, codeText, inlineCode, link;
		private int addFg, addBg, delFg, delBg, keycapText, needsYou, error, pending, working, done, idle;
		private boolean shadow;
		private int row, toolRow, listRow, margin, gap, pad;

		private Builder(ChatTheme t) {
			sprites = t.sprites; backdrop = t.backdrop; backdropBottom = t.backdropBottom; panel = t.panel; panelHi = t.panelHi;
			inset = t.inset; edge = t.edge; accent = t.accent; brass = t.brass; text = t.text; muted = t.muted; disabled = t.disabled;
			ghost = t.ghost; selected = t.selected; hover = t.hover; you = t.you; youText = t.youText; agent = t.agent;
			agentText = t.agentText; tool = t.tool; toolArg = t.toolArg; result = t.result; errorText = t.errorText; codeBg = t.codeBg;
			codeText = t.codeText; inlineCode = t.inlineCode; link = t.link; addFg = t.addFg; addBg = t.addBg; delFg = t.delFg;
			delBg = t.delBg; keycapText = t.keycapText; needsYou = t.needsYou; error = t.error; pending = t.pending; working = t.working;
			done = t.done; idle = t.idle; shadow = t.shadow; row = t.row; toolRow = t.toolRow; listRow = t.listRow; margin = t.margin;
			gap = t.gap; pad = t.pad;
		}

		public Builder sprites(@Nullable String v) { sprites = v; return this; }
		public Builder backdrop(int v) { backdrop = v; return this; }
		public Builder backdropBottom(int v) { backdropBottom = v; return this; }
		public Builder panel(int v) { panel = v; return this; }
		public Builder panelHi(int v) { panelHi = v; return this; }
		public Builder inset(int v) { inset = v; return this; }
		public Builder edge(int v) { edge = v; return this; }
		public Builder accent(int v) { accent = v; return this; }
		public Builder brass(int v) { brass = v; return this; }
		public Builder text(int v) { text = v; return this; }
		public Builder muted(int v) { muted = v; return this; }
		public Builder disabled(int v) { disabled = v; return this; }
		public Builder ghost(int v) { ghost = v; return this; }
		public Builder selected(int v) { selected = v; return this; }
		public Builder hover(int v) { hover = v; return this; }
		public Builder you(int v) { you = v; return this; }
		public Builder youText(int v) { youText = v; return this; }
		public Builder agent(int v) { agent = v; return this; }
		public Builder agentText(int v) { agentText = v; return this; }
		public Builder tool(int v) { tool = v; return this; }
		public Builder toolArg(int v) { toolArg = v; return this; }
		public Builder result(int v) { result = v; return this; }
		public Builder errorText(int v) { errorText = v; return this; }
		public Builder codeBg(int v) { codeBg = v; return this; }
		public Builder codeText(int v) { codeText = v; return this; }
		public Builder inlineCode(int v) { inlineCode = v; return this; }
		public Builder link(int v) { link = v; return this; }
		public Builder addFg(int v) { addFg = v; return this; }
		public Builder addBg(int v) { addBg = v; return this; }
		public Builder delFg(int v) { delFg = v; return this; }
		public Builder delBg(int v) { delBg = v; return this; }
		public Builder keycapText(int v) { keycapText = v; return this; }
		public Builder needsYou(int v) { needsYou = v; return this; }
		public Builder error(int v) { error = v; return this; }
		public Builder pending(int v) { pending = v; return this; }
		public Builder working(int v) { working = v; return this; }
		public Builder done(int v) { done = v; return this; }
		public Builder idle(int v) { idle = v; return this; }
		public Builder shadow(boolean v) { shadow = v; return this; }
		public Builder row(int v) { row = Math.max(9, v); return this; }
		public Builder toolRow(int v) { toolRow = Math.max(9, v); return this; }
		public Builder listRow(int v) { listRow = Math.max(10, v); return this; }
		public Builder margin(int v) { margin = Math.max(0, v); return this; }
		public Builder gap(int v) { gap = Math.max(0, v); return this; }
		public Builder pad(int v) { pad = Math.max(0, v); return this; }

		public ChatTheme build() {
			return new ChatTheme(sprites, backdrop, backdropBottom, panel, panelHi, inset, edge, accent, brass, text, muted, disabled, ghost,
					selected, hover, you, youText, agent, agentText, tool, toolArg, result, errorText, codeBg, codeText, inlineCode, link, addFg,
					addBg, delFg, delBg, keycapText, needsYou, error, pending, working, done, idle, shadow, row, toolRow, listRow, margin, gap,
					pad);
		}
	}
}
