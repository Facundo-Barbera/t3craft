package dev.t3craft.build;

/**
 * {@link BuildService#build} refused by the survival policy. {@link #getMessage()} is already
 * translated for the player; {@link #langKey()} is the lang key behind it ({@code t3craft.survival.*}).
 */
public final class BuildDeniedException extends IllegalStateException {
	private final String langKey;

	public BuildDeniedException(String langKey, String message) {
		super(message);
		this.langKey = langKey;
	}

	public String langKey() {
		return langKey;
	}
}
