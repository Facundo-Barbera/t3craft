package dev.t3craft.client.npc.behavior;

/**
 * Small expressions shown in the speech bubble / over the head. {@link #glyph} is plain text from
 * the vanilla font; {@link #particle} is a key the director maps to a particle type (or null).
 */
public enum Emote {
	NONE("", null),
	TYPING("⌨", "working"),
	EXCLAIM("!", "attention"),
	ERROR("✖", "error"),
	WAVE("o/", null),
	LOOK_AROUND("?", null),
	MUSIC("♪", "note"),
	CELEBRATE("★", "celebrate"),
	SLEEPY("z", null);

	public final String glyph;
	public final String particle;

	Emote(String glyph, String particle) {
		this.glyph = glyph;
		this.particle = particle;
	}
}
