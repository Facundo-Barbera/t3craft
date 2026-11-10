package dev.t3craft.client.blocks.config;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a binding picker can offer, per {@link Binding.Kind}: the threads the client knows, their projects and
 * environments (the same keys {@code /t3craft blocks link} takes: {@code thread:<npcKey>},
 * {@code project:<envId>:<projectId>}, {@code env:<envId>}), the board slots and the HQ beacon. A snapshot: build
 * one when the page opens ({@link #live()}) or by hand (the gallery).
 */
public final class BindingChoices {
	/** Board slots offered (board:0 is the most urgent thread). */
	public static final int BOARD_SLOTS = 8;

	/**
	 * One entry of the picker.
	 *
	 * @param detail a second, muted part of the label (the project of a thread, the environment of a project)
	 * @param status a thread's status (IDLE for the other kinds)
	 * @param project / env the thread's project and environment keys (empty for the other kinds)
	 */
	public record Choice(Binding binding, String label, String detail, StationStatus status, String project, String env) {
		public Choice {
			label = label == null || label.isBlank() ? binding.value() : label;
			detail = detail == null ? "" : detail;
			status = status == null ? StationStatus.IDLE : status;
			project = project == null ? "" : project;
			env = env == null ? "" : env;
		}

		/** The text shown in the select. */
		public String text() {
			return detail.isEmpty() ? label : label + "  -  " + detail;
		}

		/** True when the search {@code q} (lower case) is in the label, the detail or the key. */
		boolean matches(String q) {
			return q.isEmpty() || label.toLowerCase(Locale.ROOT).contains(q) || detail.toLowerCase(Locale.ROOT).contains(q)
					|| binding.value().toLowerCase(Locale.ROOT).contains(q);
		}
	}

	private final List<Choice> threads;
	private final List<Choice> projects;
	private final List<Choice> envs;

	public BindingChoices(List<Choice> threads, List<Choice> projects, List<Choice> envs) {
		this.threads = List.copyOf(threads);
		this.projects = List.copyOf(projects);
		this.envs = List.copyOf(envs);
	}

	/** From the threads the client knows now (empty when the mod is not connected). */
	public static BindingChoices live() {
		T3CraftClient c = T3CraftClient.get();
		return fromNpcs(c == null ? List.of() : c.npcs().all());
	}

	public static BindingChoices fromNpcs(List<NpcInfo> npcs) {
		List<Choice> threads = new ArrayList<>();
		Map<String, Choice> projects = new LinkedHashMap<>();
		Map<String, Choice> envs = new LinkedHashMap<>();
		for (NpcInfo n : npcs) {
			WorkstationThread t = WorkstationThread.fromNpc(n);
			String envLabel = n.envLabel() == null || n.envLabel().isBlank() ? t.envId() : n.envLabel();
			String projectLabel = n.groupLabel() == null || n.groupLabel().isBlank() ? t.groupId() : n.groupLabel();
			threads.add(new Choice(Binding.thread(t.key()), t.title(), projectLabel, t.status(), t.groupId(), t.envId()));
			if (!t.groupId().isEmpty()) {
				projects.putIfAbsent(t.groupId(), new Choice(Binding.project(t.groupId()), projectLabel, envLabel, StationStatus.IDLE, "", ""));
			}
			if (!t.envId().isEmpty()) {
				envs.putIfAbsent(t.envId(), new Choice(Binding.env(t.envId()), envLabel, "", StationStatus.IDLE, "", ""));
			}
		}
		Comparator<Choice> byLabel = Comparator.comparing((Choice c) -> c.label().toLowerCase(Locale.ROOT)).thenComparing(c -> c.binding().value());
		threads.sort(Comparator.comparing((Choice c) -> c.status() == StationStatus.ARCHIVED).thenComparing(byLabel));
		List<Choice> p = new ArrayList<>(projects.values());
		p.sort(byLabel);
		List<Choice> e = new ArrayList<>(envs.values());
		e.sort(byLabel);
		return new BindingChoices(threads, p, e);
	}

	/** The known threads (for previews: a feed's rows, the status counts). */
	public List<Choice> threads() {
		return threads;
	}

	/** Every choice of {@code kind} whose text contains {@code query} (case-insensitive). */
	public List<Choice> of(Binding.Kind kind, String query) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<Choice> all = switch (kind) {
			case THREAD -> threads;
			case PROJECT -> projects;
			case ENV -> envs;
			case BOARD -> boardSlots();
			case BEACON -> List.of(beacon());
			default -> List.of();
		};
		if (q.isEmpty()) {
			return all;
		}
		List<Choice> out = new ArrayList<>();
		for (Choice c : all) {
			if (c.matches(q)) {
				out.add(c);
			}
		}
		return out;
	}

	/** The choice for {@code b}, or a stand-in when the client does not know it (a thread of an environment not connected). */
	public Choice find(Binding b) {
		for (Choice c : of(b.kind(), "")) {
			if (c.binding().equals(b)) {
				return c;
			}
		}
		return new Choice(b, b.value(), b.isNone() ? "" : "not connected", StationStatus.IDLE, "", "");
	}

	/** The choice for {@code b} when known, else null. */
	public @Nullable Choice known(Binding b) {
		for (Choice c : of(b.kind(), "")) {
			if (c.binding().equals(b)) {
				return c;
			}
		}
		return null;
	}

	private static List<Choice> boardSlots() {
		List<Choice> out = new ArrayList<>(BOARD_SLOTS);
		for (int i = 0; i < BOARD_SLOTS; i++) {
			out.add(new Choice(Binding.board(i), "Slot " + (i + 1), i == 0 ? "most urgent thread" : "", StationStatus.IDLE, "", ""));
		}
		return out;
	}

	private static Choice beacon() {
		return new Choice(Binding.BEACON, "Whole HQ", "every thread", StationStatus.IDLE, "", "");
	}

	/** Default label of a kind in a picker. */
	public static String kindLabel(Binding.Kind kind) {
		return switch (kind) {
			case NONE -> "Unbound";
			case THREAD -> "Thread";
			case PROJECT -> "Project";
			case ENV -> "Environment";
			case BOARD -> "Board slot";
			case BEACON -> "Beacon";
			case MODULE -> "Module";
		};
	}
}
