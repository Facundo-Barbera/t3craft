// Turn layout (speaker turns, collapsed tool runs) adapted from AgentCraft's console
// (https://github.com/blendi-remade/agentcraft). Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.chat;

import dev.t3craft.client.chat.ChatEntry.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Groups transcript entries into conversation turns for the bubble layout. Pure Java (tested).
 *
 * <ul>
 *   <li>Every user message is its own {@link Role#USER} turn.</li>
 *   <li>Everything between two user messages is one {@link Role#AGENT} turn.</li>
 *   <li>Inside an agent turn, consecutive tool-like entries (tools, subagents, thinking) become one
 *   {@link ToolRun}, drawn collapsed as a single line ("3 tools · Bash, Edit, Read").</li>
 *   <li>System notes are {@link Role#NOTICE} turns of their own (a centred line).</li>
 * </ul>
 */
public final class ChatTurns {
	private ChatTurns() {
	}

	public enum Role {
		USER, AGENT, NOTICE
	}

	/** A piece of a turn. */
	public sealed interface Block permits Message, ToolRun {
		/** Stable key (fold state survives relayout and new entries). */
		String key();
	}

	/** A message-like entry (assistant text, plan, to-do, request, error, user text). */
	public record Message(ChatEntry entry) implements Block {
		@Override
		public String key() {
			return entry.key();
		}
	}

	/** Consecutive tool-like entries; {@code key} is the first entry's key prefixed with {@code run:}. */
	public record ToolRun(String key, List<ChatEntry> entries) implements Block {
		public ToolRun {
			entries = List.copyOf(entries);
		}

		/** "3 tools · Bash, Edit +1" style summary: count, then distinct labels in order (at most {@code max}). */
		public String summary(int max) {
			Map<String, Integer> labels = new LinkedHashMap<>();
			for (ChatEntry e : entries) {
				labels.merge(label(e), 1, Integer::sum);
			}
			StringBuilder b = new StringBuilder();
			int n = entries.size();
			boolean allThinking = entries.stream().allMatch(e -> e.kind() == Kind.REASONING);
			b.append(n).append(allThinking ? (n == 1 ? " thought" : " thoughts") : (n == 1 ? " step" : " steps"));
			if (!allThinking) {
				b.append(" · ");
				int i = 0;
				for (String l : labels.keySet()) {
					if (i == max) {
						b.append(" +").append(labels.size() - max);
						break;
					}
					if (i > 0) {
						b.append(", ");
					}
					b.append(l);
					i++;
				}
			}
			return b.toString();
		}

		public boolean running() {
			return entries.stream().anyMatch(e -> e.streaming() || "running".equals(e.status()) || "pending".equals(e.status()));
		}

		public boolean failed() {
			return entries.stream().anyMatch(e -> "failed".equals(e.status()));
		}
	}

	/** One speaker turn: role, blocks, time of its first timed entry. */
	public record Turn(Role role, List<Block> blocks, @Nullable Long at) {
		public Turn {
			blocks = List.copyOf(blocks);
		}

		public String key() {
			return blocks.isEmpty() ? "" : blocks.getFirst().key();
		}
	}

	/** Short label of a tool-like entry: the tool title's first word ("$ ls -la" → "$"), else its role. */
	static String label(ChatEntry e) {
		if (e.kind() == Kind.REASONING) {
			return "thinking";
		}
		if (e.kind() == Kind.SUBAGENT) {
			return "subagent";
		}
		String t = e.title().strip();
		if (t.isEmpty()) {
			return e.kind().role;
		}
		int sp = t.indexOf(' ');
		String w = sp < 0 ? t : t.substring(0, sp);
		return w.length() > 16 ? w.substring(0, 15) + "…" : w;
	}

	static boolean toolLike(Kind k) {
		return k == Kind.TOOL || k == Kind.SUBAGENT || k == Kind.REASONING;
	}

	public static List<Turn> group(List<ChatEntry> entries) {
		List<Turn> out = new ArrayList<>();
		List<Block> blocks = new ArrayList<>();
		List<ChatEntry> run = new ArrayList<>();
		Long[] at = {null};
		Runnable flushRun = () -> {
			if (!run.isEmpty()) {
				blocks.add(new ToolRun("run:" + run.getFirst().key(), run));
				run.clear();
			}
		};
		Runnable flushTurn = () -> {
			flushRun.run();
			if (!blocks.isEmpty()) {
				out.add(new Turn(Role.AGENT, blocks, at[0]));
				blocks.clear();
			}
			at[0] = null;
		};
		for (ChatEntry e : entries) {
			if (e.kind() == Kind.USER) {
				flushTurn.run();
				out.add(new Turn(Role.USER, List.of(new Message(e)), e.at()));
				continue;
			}
			if (e.kind() == Kind.SYSTEM) {
				flushTurn.run();
				out.add(new Turn(Role.NOTICE, List.of(new Message(e)), e.at()));
				continue;
			}
			if (at[0] == null) {
				at[0] = e.at();
			}
			if (toolLike(e.kind())) {
				run.add(e);
			} else {
				flushRun.run();
				blocks.add(new Message(e));
			}
		}
		flushTurn.run();
		return out;
	}
}
