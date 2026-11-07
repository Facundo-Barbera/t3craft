package dev.t3craft.survival;

/**
 * The survival policy, as a small interface other features consult instead of checking game modes
 * themselves. Get the live one with {@link Survival#rules()}; build one for tests with
 * {@link GameModePolicy#GameModePolicy(SurvivalSettings)}.
 * <p>Integration points (see {@code docs/feat-survival.md}):
 * <ul>
 *   <li><b>portable</b> (key that opens the terminal): {@link #openTerminal}.</li>
 *   <li><b>builders / site</b> before submitting a job: {@link #buildInWorld}, then
 *       {@link #materials} to decide whether to charge a {@link MaterialBill}.</li>
 *   <li><b>debug commands</b> that edit the world for free ({@code /t3craft debug build-demo}): {@link #debugWorldEdit}.</li>
 * </ul>
 * Implementations are pure and thread-safe: no Minecraft calls, no I/O.
 */
public interface SurvivalRules {
	/** May the player open the T3 Terminal screen with the key (the item itself always works)? */
	Verdict openTerminal(PlayContext ctx);

	/** May mod builders place/remove blocks in the world on behalf of this player at all? */
	Verdict buildInWorld(PlayContext ctx);

	/** Free blocks, or taken from the player's inventory. Only meaningful if {@link #buildInWorld} allowed it. */
	MaterialSource materials(PlayContext ctx);

	/** May a debug command edit the world for free (structures out of nothing)? */
	Verdict debugWorldEdit(PlayContext ctx);

	/** Whether to grant the "First contact" advancement when the terminal is used. */
	boolean firstContactAdvancement();
}
