package dev.t3craft.hq.lead;

import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office: the shared lead roster and the convention that puts a lead in its office.
 *
 * <h2>How a lead gets an office in every layout</h2>
 * The HQ roster ({@code HqInput}) puts a lead in a project of its own, {@link #projectKey}
 * ({@code lead:<npcId>}), instead of its real project. Every layout's allocator then gives it a module
 * (a wing, a barrio, a hall, a floor) of its own, kept at the same index for as long as the lead holds
 * the office (the allocator's "modules kept" rule), so the office never jumps. Its desk anchor
 * ({@code desk_<npcId>}) is in that module, so its NPC walks there and works there with no other change.
 * {@code HqLayout.Module.type()} says {@code LEAD_OFFICE} for such a module: a layout that knows how
 * draws a real office ({@code LeadOfficeRoom} for classic, {@code citadel.CitadelLeadOffice} for the
 * citadel); the others get the generic fallback ({@code LeadOfficeDecor}: an extra screen and a banner
 * by the desk). The module's big screen binding {@code project:lead:<npcId>} shows the lead's children
 * ({@code client.blocks.LeadFeed}).
 *
 * <p>The roster is written by the client ({@code client.hq.lead.LeadOfficeClient}) and read by any
 * thread; it is an immutable value behind a volatile field.
 */
public final class LeadOffices {
	/** Prefix of a lead's own project key. */
	public static final String PREFIX = "lead:";
	/** Key prefix of the extra big screen the fallback decor adds ({@code lead@<module>}). */
	public static final String SCREEN_KEY = "lead@";

	private static volatile LeadRoster current = LeadRoster.EMPTY;
	private static volatile int version;

	private LeadOffices() {
	}

	/** The roster in force (empty until the client computes one, and in bridge mode). */
	public static LeadRoster current() {
		return current;
	}

	/** Bumped each time {@link #current()} changes content (memo key for screens and plates). */
	public static int version() {
		return version;
	}

	/** Installs a roster (null = empty). Returns true when it changed. */
	public static boolean use(@Nullable LeadRoster r) {
		LeadRoster next = r == null ? LeadRoster.EMPTY : r;
		if (next.equals(current)) {
			return false;
		}
		current = next;
		version++;
		return true;
	}

	/** The project key of a lead's office: {@code lead:<npcId>}. */
	public static String projectKey(String npcId) {
		return PREFIX + npcId;
	}

	/** True for a lead office's project key. */
	public static boolean isLeadProject(@Nullable String projectKey) {
		return projectKey != null && projectKey.startsWith(PREFIX) && projectKey.length() > PREFIX.length();
	}

	/** The lead's NPC id of a lead office's project key, or null. */
	public static @Nullable String leadOf(@Nullable String projectKey) {
		return isLeadProject(projectKey) ? projectKey.substring(PREFIX.length()) : null;
	}

	/** Sign / label of a lead's office: "★ " + its real project's label. */
	public static String label(String projectLabel) {
		return "★ " + (projectLabel == null || projectLabel.isBlank() ? "Lead" : projectLabel);
	}
}
