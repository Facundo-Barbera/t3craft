package dev.t3craft.client.hq.lead;

import dev.t3craft.hq.lead.LeadDetector;
import dev.t3craft.hq.lead.LeadOffices;
import dev.t3craft.hq.lead.LeadPolicy;
import dev.t3craft.hq.lead.LeadRoster;
import dev.t3craft.t3.LeadSignals;
import dev.t3craft.t3.NpcView;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office, client side: runs the {@link LeadDetector} on the shells the direct source already
 * has (no new connection or call), publishes the roster ({@link LeadOffices#use}) and adds the
 * "Coordinating N agents" line to a lead's NPC panel.
 *
 * <p>Cost: {@link #refresh} is called from the direct refresh (client thread, not the render pass) and
 * does its work at most once every {@link #PERIOD_MS}: one pass over each environment's shells. The HQ
 * picks the roster up through its normal path ({@code HqConstructionClient.liveRoster}: a lead goes to
 * its own project), so the re-plan runs on the HQ's async target computation, debounced like any
 * roster change, and only while the HQ is open (presence gate).
 */
public final class LeadOfficeClient {
	/** The detector runs at most this often. */
	public static final long PERIOD_MS = 2_000;

	private static final LeadDetector DETECTOR = new LeadDetector();
	private static long lastRun;
	/** feat/lead-review-walk: NPC ids of live subagents/delegated tasks whose parent has a desk (they report to it). */
	private static volatile Set<String> reportsToParent = Set.of();

	private LeadOfficeClient() {
	}

	/**
	 * Direct refresh hook. {@code deskViews}: the NPCs with a desk (subagents already filtered out);
	 * only those may become leads.
	 */
	public static synchronized void refresh(Map<String, ShellState.View> shells, List<NpcView> deskViews, long nowMs) {
		if (nowMs - lastRun < PERIOD_MS && nowMs >= lastRun) {
			return;
		}
		lastRun = nowMs;
		LeadPolicy policy = LeadPolicy.active();
		if (!policy.enabled()) {
			DETECTOR.clear();
			reportsToParent = Set.of();
			LeadOffices.use(LeadRoster.EMPTY);
			return;
		}
		Map<String, LeadSignals> byEnv = new HashMap<>();
		for (Map.Entry<String, ShellState.View> e : shells.entrySet()) {
			if (e.getValue() != null && e.getValue().threads() != null) {
				byEnv.put(e.getKey(), LeadSignals.of(e.getValue().threads()));
			}
		}
		Set<String> eligible = new HashSet<>();
		for (NpcView v : deskViews) {
			if (!v.settled()) {
				eligible.add(v.id());
			}
		}
		LeadRoster roster = DETECTOR.update(byEnv, eligible::contains, policy, nowMs);
		if (policy.childrenWaitForReview()) {
			Set<String> reports = new HashSet<>();
			roster = roster.withReviews(reviews(shells, eligible, roster, reports));
			reportsToParent = Set.copyOf(reports);
		} else {
			reportsToParent = Set.of();
		}
		LeadOffices.use(roster);
	}

	/**
	 * feat/lead-review-walk: the children waiting for their parent's review. A child counts when it is a
	 * subagent or delegated task with an NPC of its own, alive (not archived, settled or deleted), whose
	 * parent has a desk; it waits when it is done, or idle after finishing a turn. One pass over each
	 * environment's shells.
	 *
	 * @param eligible NPC ids with a desk (not settled)
	 * @param reports  filled with every such child, finished or not (they never notify the player when done)
	 */
	static Map<String, LeadRoster.Review> reviews(Map<String, ShellState.View> shells, Set<String> eligible, LeadRoster roster, Set<String> reports) {
		Map<String, LeadRoster.Review> out = new java.util.TreeMap<>();
		for (Map.Entry<String, ShellState.View> e : shells.entrySet()) {
			ShellState.View view = e.getValue();
			if (view == null || view.threads() == null) {
				continue;
			}
			String env = e.getKey();
			Map<String, ThreadShell> byId = new HashMap<>();
			for (ThreadShell t : view.threads()) {
				if (t != null) {
					byId.put(t.id(), t);
				}
			}
			for (ThreadShell t : byId.values()) {
				String parent = t.parentThreadId();
				if (!t.isSubagent() || parent == null || parent.isEmpty() || parent.equals(t.id())) {
					continue;
				}
				if (t.archivedAt() != null || t.settledAt() != null || t.deletedAt() != null) {
					continue;
				}
				String child = env + ":" + t.id();
				String parentNpc = env + ":" + parent;
				if (!eligible.contains(child) || !eligible.contains(parentNpc)) {
					continue;
				}
				reports.add(child);
				ThreadState s = ThreadState.of(t);
				if (s == ThreadState.DONE || (s == ThreadState.IDLE && t.latestRunCompletedAt() != null)) {
					ThreadShell p = byId.get(parent);
					String title = p == null || p.title() == null || p.title().isBlank() ? parent : p.title();
					out.put(child, new LeadRoster.Review(parentNpc, title, roster.isLead(parentNpc)));
				}
			}
		}
		return out;
	}

	/**
	 * feat/lead-review-walk: true when {@code npcId} is a child that reports to its parent (who reviews
	 * its work), so its "thread finished" notice is not for the player. False with
	 * {@code leadOffice.childrenWaitForReview} off.
	 */
	public static boolean reportsToParent(String npcId) {
		LeadPolicy p = LeadPolicy.active();
		return p.enabled() && p.childrenWaitForReview() && reportsToParent.contains(npcId);
	}

	/** Bridge mode, disconnect or world change: no leads. */
	public static synchronized void clear() {
		DETECTOR.clear();
		lastRun = 0;
		reportsToParent = Set.of();
		LeadOffices.use(LeadRoster.EMPTY);
	}

	/**
	 * "Coordinating N agents" for a lead's NPC id; feat/lead-review-walk: "Awaiting review from &lt;parent&gt;"
	 * for a finished child waiting for its parent; null for anyone else.
	 */
	public static @Nullable Component plate(String npcId) {
		LeadRoster roster = LeadOffices.current();
		LeadRoster.Lead l = roster.lead(npcId);
		if (l == null) {
			LeadRoster.Review r = roster.reviews().get(npcId);
			return r == null ? null : Component.translatable("t3craft.lead.awaitingReview", r.parentTitle()).withStyle(ChatFormatting.GOLD);
		}
		int n = l.coordinating() > 0 ? l.coordinating() : l.liveChildren().size();
		return Component.translatable("t3craft.lead.coordinating", n).withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	/** Panel hook that keeps another one's line and adds {@link #plate} in front of it. */
	public static Function<String, @Nullable Component> withPlate(Function<String, @Nullable Component> other) {
		return id -> {
			Component a = plate(id);
			Component b = other == null ? null : other.apply(id);
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

	/**
	 * The HQ project a roster line goes to: a lead's own office project ({@code lead:<npcId>}) while it
	 * holds an office and keeps its desk, else its real project. {@code [key, label]}.
	 */
	public static String[] project(String npcId, String groupId, String groupLabel, boolean released) {
		if (!released && LeadOffices.current().isLead(npcId)) {
			return new String[] {LeadOffices.projectKey(npcId), LeadOffices.label(groupLabel)};
		}
		return new String[] {groupId, groupLabel};
	}
}
