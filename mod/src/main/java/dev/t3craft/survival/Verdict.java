package dev.t3craft.survival;

/**
 * Answer of a {@link SurvivalRules} check. {@code reason} is a stable key (also the suffix of the lang
 * key {@code t3craft.survival.<reason>}) so callers can show a translated message; {@code "ok"} when allowed.
 */
public record Verdict(boolean allowed, String reason) {
	public static final Verdict OK = new Verdict(true, "ok");

	public static Verdict deny(String reason) {
		return new Verdict(false, reason);
	}

	public String langKey() {
		return "t3craft.survival." + reason;
	}
}
