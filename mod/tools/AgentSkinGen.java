import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Draws the mod's own agent skins (64x64, classic player layout) procedurally: no player or Mojang
 * skin is used as a base. Output (relative to the given directory, normally
 * {@code src/main/resources/assets/t3craft/textures/entity/agent}):
 *
 * <ul>
 *   <li>{@code <character>_<shirt>.png}: one per character and shirt colour</li>
 *   <li>{@code apprentice/<character>_<shirt>.png}: the helper (subagent) variant: same face and
 *       hair, plus an apron and a cap</li>
 *   <li>{@code default.png}: neutral fallback</li>
 * </ul>
 *
 * Characters, the model (wide/slim) and the shirt palette must match
 * {@code dev.t3craft.client.npc.AgentSkins}. Regenerate with (from {@code mod/}):
 * {@code java tools/AgentSkinGen.java src/main/resources/assets/t3craft/textures/entity/agent}
 */
public final class AgentSkinGen {
	/** Same tones as {@code EnvBadge.PALETTE}, so a shirt matches its environment's badge. */
	static final int[] SHIRTS = {0x2F7F8A, 0x8A5A2F, 0x5A6FA8, 0x7A8A2F, 0x8A2F5E, 0x2F8A55, 0x8A7A2F, 0x6A4A8A};

	enum Hair { SHORT, LONG, BUZZ, PONYTAIL, CURLY, SPIKY }

	enum Extra { NONE, GLASSES, BEARD, HEADPHONES, BEANIE, FRECKLES }

	record Character(String id, boolean slim, int tone, int hair, Hair style, int eyes, int pants, int shoes, Extra extra) {
	}

	static final Character[] CHARACTERS = {
			new Character("ada", true, 0xF1C9A5, 0x3B2416, Hair.LONG, 0x3C6E3C, 0x2B3550, 0x2A2A2A, Extra.GLASSES),
			new Character("bruno", false, 0xC68E62, 0x1E1510, Hair.SHORT, 0x4A3020, 0x3E3A33, 0x5A3A22, Extra.BEARD),
			new Character("chen", false, 0xE8C29A, 0x101010, Hair.SPIKY, 0x2A1E14, 0x2A2F3A, 0xDDDDDD, Extra.HEADPHONES),
			new Character("dalia", true, 0x8D5A3B, 0x140C08, Hair.CURLY, 0x3A2414, 0x4B3B2A, 0x7A2E2E, Extra.NONE),
			new Character("emil", false, 0xF5D7B8, 0xC9A04A, Hair.SHORT, 0x3A6EA5, 0x34405A, 0x3A2A1E, Extra.FRECKLES),
			new Character("fatima", true, 0xB07A52, 0x2A1A12, Hair.PONYTAIL, 0x3A2A1A, 0x2F2F38, 0x222222, Extra.GLASSES),
			new Character("gus", false, 0x6E4630, 0x0E0A08, Hair.BUZZ, 0x2A1A10, 0x44403A, 0xB0B0B0, Extra.BEARD),
			new Character("hana", true, 0xF0CDA8, 0x0F0F14, Hair.LONG, 0x241A12, 0x3A3046, 0xC9C9C9, Extra.HEADPHONES),
			new Character("ivo", false, 0xD9A77E, 0x8A3A1A, Hair.CURLY, 0x4E7A3A, 0x2E3A2E, 0x4A2E1E, Extra.BEANIE),
			new Character("juno", true, 0xE2B48C, 0x6A3C8E, Hair.SPIKY, 0x3A3A6A, 0x262A36, 0x1E1E1E, Extra.FRECKLES),
			new Character("kofi", false, 0x5A3826, 0x0C0806, Hair.SHORT, 0x2A180E, 0x3A3A44, 0xE0E0E0, Extra.GLASSES),
			new Character("lia", true, 0xFADBC4, 0xD8D0C0, Hair.PONYTAIL, 0x5A7AA8, 0x45383A, 0x5A3A2A, Extra.NONE),
	};

	static final Character DEFAULT = new Character("default", false, 0xD8B08A, 0x4A3A2A, Hair.SHORT, 0x3A3A3A, 0x3A3A40, 0x2A2A2A,
			Extra.NONE);
	static final int DEFAULT_SHIRT = 0x7A7A80;

	public static void main(String[] args) throws IOException {
		File out = new File(args.length > 0 ? args[0] : ".");
		new File(out, "apprentice").mkdirs();
		for (Character c : CHARACTERS) {
			for (int s = 0; s < SHIRTS.length; s++) {
				ImageIO.write(draw(c, SHIRTS[s], false), "png", new File(out, c.id + "_" + s + ".png"));
				ImageIO.write(draw(c, SHIRTS[s], true), "png", new File(out, "apprentice/" + c.id + "_" + s + ".png"));
			}
		}
		ImageIO.write(draw(DEFAULT, DEFAULT_SHIRT, false), "png", new File(out, "default.png"));
	}

	// ---- drawing ----

	private final BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
	private final Random rnd;

	private AgentSkinGen(long seed) {
		rnd = new Random(seed);
	}

	static BufferedImage draw(Character c, int shirt, boolean apprentice) {
		AgentSkinGen g = new AgentSkinGen(c.id.hashCode() * 31L + shirt + (apprentice ? 7 : 0));
		g.paint(c, shirt, apprentice);
		return g.img;
	}

	private void paint(Character c, int shirt, boolean apprentice) {
		int arm = c.slim ? 3 : 4;
		// Base: every face of every box gets a lightly noised fill.
		box(0, 0, 8, 8, 8, c.tone, 6); // head
		box(16, 16, 8, 12, 4, shirt, 10); // body
		box(40, 16, arm, 12, 4, shirt, 10); // right arm
		box(32, 48, arm, 12, 4, shirt, 10); // left arm
		box(0, 16, 4, 12, 4, c.pants, 8); // right leg
		box(16, 48, 4, 12, 4, c.pants, 8); // left leg

		// Hands (bottom 3 rows of arms, all sides) and arm bottom faces.
		armSkin(40, 16, arm, c.tone);
		armSkin(32, 48, arm, c.tone);
		// Shoes (bottom 2 rows of legs).
		legShoes(0, 16, c.shoes);
		legShoes(16, 48, c.shoes);

		shirtDetails(shirt);
		face(c);
		hair(c);
		if (apprentice) {
			apron();
			cap(shirt);
		}
	}

	/** Fills the six faces of a box whose texture origin is (u, v). */
	private void box(int u, int v, int w, int h, int d, int rgb, int noise) {
		rect(u + d, v, w, d, rgb, noise); // top
		rect(u + d + w, v, w, d, shade(rgb, -0.12), noise); // bottom
		rect(u, v + d, d, h, shade(rgb, -0.08), noise); // right
		rect(u + d, v + d, w, h, rgb, noise); // front
		rect(u + d + w, v + d, d, h, shade(rgb, -0.08), noise); // left
		rect(u + d + w + d, v + d, w, h, shade(rgb, -0.14), noise); // back
	}

	private void armSkin(int u, int v, int w, int tone) {
		int d = 4;
		int y0 = v + d + 12 - 3;
		rect(u, y0, d + w + d + w, 3, tone, 5);
		rect(u + d + w, v, w, d, shade(tone, -0.1), 4); // bottom face (palm)
		// Cuff line above the hand.
		for (int x = u; x < u + d + w + d + w; x++) {
			set(x, y0 - 1, shade(get(x, y0 - 1), -0.25));
		}
	}

	private void legShoes(int u, int v, int shoes) {
		int y0 = v + 4 + 12 - 2;
		rect(u, y0, 16, 2, shoes, 4);
		rect(u + 8, v, 4, 4, shade(shoes, -0.2), 2); // sole
	}

	private void shirtDetails(int shirt) {
		// Body front at (20,20) 8x12: collar, placket, hem.
		int collar = shade(shirt, 0.35);
		set(22, 20, collar);
		set(23, 21, collar);
		set(24, 21, collar);
		set(25, 20, collar);
		for (int y = 22; y < 30; y++) {
			set(23, y, shade(shirt, -0.18));
		}
		set(24, 24, shade(shirt, 0.45));
		set(24, 27, shade(shirt, 0.45));
		for (int x = 16; x < 40; x++) {
			set(x, 31, shade(get(x, 31), -0.2)); // hem
		}
		// Belt across the top of the legs.
		for (int x = 0; x < 16; x++) {
			set(x, 20, shade(get(x, 20), -0.35));
		}
		for (int x = 16; x < 32; x++) {
			set(x, 52, shade(get(x, 52), -0.35));
		}
	}

	private void face(Character c) {
		// Head front at (8,8) 8x8.
		int white = 0xF4F4F0;
		set(9, 12, white);
		set(10, 12, c.eyes);
		set(13, 12, c.eyes);
		set(14, 12, white);
		int brow = shade(c.hair, -0.1);
		set(9, 11, brow);
		set(10, 11, brow);
		set(13, 11, brow);
		set(14, 11, brow);
		set(11, 13, shade(c.tone, -0.15)); // nose
		set(12, 13, shade(c.tone, -0.15));
		int mouth = shade(c.tone, -0.35);
		set(11, 14, mouth);
		set(12, 14, mouth);
		switch (c.extra) {
			case GLASSES -> {
				int frame = 0x1E1E22;
				for (int x = 8; x < 16; x++) {
					if (x != 11 && x != 12) {
						set(x, 11, frame);
					}
				}
				set(11, 12, frame);
				set(12, 12, frame);
				set(8, 12, frame);
				set(15, 12, frame);
				// Hat layer: temples on the sides.
				set(32 + 7, 8 + 3, frame);
				set(32 + 16, 8 + 3, frame);
			}
			case BEARD -> {
				int b = shade(c.hair, 0.05);
				for (int x = 8; x < 16; x++) {
					set(x, 15, b);
				}
				set(8, 14, b);
				set(9, 14, b);
				set(10, 14, b);
				set(13, 14, b);
				set(14, 14, b);
				set(15, 14, b);
				set(8, 13, b);
				set(15, 13, b);
				for (int y = 13; y < 16; y++) {
					set(6, y, b); // right side, next to the face
					set(7, y, b);
					set(16, y, b); // left side, next to the face
					set(17, y, b);
				}
			}
			case FRECKLES -> {
				int f = shade(c.tone, -0.22);
				set(9, 13, f);
				set(14, 13, f);
				set(10, 14, f);
				set(13, 14, f);
			}
			case HEADPHONES -> {
				int hp = 0x26262C;
				int pad = 0xD0503C;
				// Band over the top (hat layer top face at (40,0) 8x8).
				for (int z = 0; z < 8; z++) {
					set(40 + 3, z, hp);
					set(40 + 4, z, hp);
				}
				// Ear cups on the hat layer sides.
				for (int y = 11; y < 15; y++) {
					for (int x = 34; x < 38; x++) {
						set(x, y, y == 11 || y == 14 ? hp : pad);
						set(x + 16, y, y == 11 || y == 14 ? hp : pad);
					}
				}
			}
			case BEANIE, NONE -> {
			}
		}
	}

	private void hair(Character c) {
		int h = c.hair;
		// Top of the head always gets hair (except a buzz cut, which is a thin shade).
		if (c.style == Hair.BUZZ) {
			rect(8, 0, 8, 8, blend(h, c.tone, 0.45), 10);
			for (int x = 8; x < 16; x++) {
				set(x, 8, blend(h, c.tone, 0.45));
			}
			for (int x = 0; x < 8; x++) {
				set(x, 8, blend(h, c.tone, 0.45));
				set(x + 16, 8, blend(h, c.tone, 0.45));
				set(x + 24, 8, blend(h, c.tone, 0.45));
			}
		} else {
			rect(8, 0, 8, 8, h, 14);
			// Fringe on the front, sides and back.
			for (int x = 8; x < 16; x++) {
				set(x, 8, h);
				set(x, 9, (x + c.id.length()) % 3 == 0 ? get(x, 9) : h);
			}
			for (int x = 0; x < 8; x++) {
				set(x, 8, h);
				set(x, 9, h);
				set(x, 10, x < 3 ? h : get(x, 10));
				set(x + 16, 8, h);
				set(x + 16, 9, h);
				set(x + 16, 10, x > 4 ? h : get(x + 16, 10));
			}
			for (int x = 24; x < 32; x++) {
				for (int y = 8; y < 13; y++) {
					set(x, y, noisy(h, 12));
				}
			}
		}
		switch (c.style) {
			case LONG -> {
				// Down the back and sides of the head, then over the shoulders on the jacket layer.
				for (int y = 8; y < 16; y++) {
					for (int x = 24; x < 32; x++) {
						set(x, y, noisy(h, 12));
					}
					for (int x = 0; x < 3; x++) {
						set(x, y, noisy(h, 12));
					}
					for (int x = 21; x < 24; x++) {
						set(x, y, noisy(h, 12));
					}
				}
				set(8, 10, h);
				set(15, 10, h);
				// Back of the body (jacket layer back at (32,36)): hair to the shoulder blades.
				for (int y = 36; y < 40; y++) {
					for (int x = 32; x < 40; x++) {
						set(x, y, noisy(h, 12));
					}
				}
			}
			case PONYTAIL -> {
				for (int y = 8; y < 16; y++) {
					for (int x = 24; x < 32; x++) {
						set(x, y, noisy(h, 12));
					}
				}
				// Tail: hat-layer back, a 2px strip hanging low, plus a tie.
				for (int y = 10; y < 16; y++) {
					set(59, y, noisy(h, 10));
					set(60, y, noisy(h, 10));
				}
				set(59, 10, 0xC04848);
				set(60, 10, 0xC04848);
			}
			case CURLY -> {
				// Volume on the hat layer: top and a lumpy fringe on all sides.
				for (int x = 40; x < 48; x++) {
					for (int y = 0; y < 8; y++) {
						if (rnd.nextInt(5) != 0) {
							set(x, y, noisy(shade(h, 0.08), 16));
						}
					}
				}
				for (int x = 32; x < 64; x++) {
					set(x, 8, noisy(h, 16));
					if ((x & 1) == 0 || x >= 56) {
						set(x, 9, noisy(h, 16));
					}
					if (x >= 56) {
						for (int y = 10; y < 14; y++) {
							set(x, y, noisy(h, 16));
						}
					}
				}
			}
			case SPIKY -> {
				for (int x = 40; x < 48; x++) {
					for (int y = 0; y < 8; y++) {
						if (((x + y) & 1) == 0) {
							set(x, y, shade(h, 0.15));
						}
					}
				}
				for (int x = 32; x < 64; x += 2) {
					set(x, 8, shade(h, 0.1));
				}
			}
			case SHORT, BUZZ -> {
			}
		}
		if (c.extra == Extra.BEANIE) {
			int b = 0xB8423A;
			for (int x = 40; x < 48; x++) {
				for (int y = 0; y < 8; y++) {
					set(x, y, noisy(b, 10));
				}
			}
			for (int x = 32; x < 64; x++) {
				set(x, 8, noisy(b, 10));
				set(x, 9, shade(b, -0.25)); // fold
			}
			set(43, 3, 0xF0F0F0); // pompom
			set(44, 3, 0xF0F0F0);
			set(43, 4, 0xF0F0F0);
			set(44, 4, 0xF0F0F0);
		}
	}

	/** Helper variant: a canvas apron on the jacket layer, front of the body. */
	private void apron() {
		int canvas = 0xD9C9A3;
		int strap = 0x8A6A44;
		// Jacket layer: body front at (20,36) 8x12.
		for (int y = 39; y < 48; y++) {
			for (int x = 21; x < 27; x++) {
				set(x, y, noisy(canvas, 8));
			}
		}
		set(21, 36, strap);
		set(21, 37, strap);
		set(21, 38, strap);
		set(26, 36, strap);
		set(26, 37, strap);
		set(26, 38, strap);
		for (int x = 20; x < 28; x++) {
			set(x, 42, strap); // waist tie
		}
		set(23, 44, shade(canvas, -0.25)); // pocket
		set(24, 44, shade(canvas, -0.25));
		set(23, 45, shade(canvas, -0.25));
		set(24, 45, shade(canvas, -0.25));
		// Ties around the sides and back.
		for (int x = 16; x < 20; x++) {
			set(x, 42, strap);
		}
		for (int x = 28; x < 40; x++) {
			set(x, 42, strap);
		}
	}

	/** Helper variant: a small cap in the shirt colour (hat layer), brim at the front. */
	private void cap(int shirt) {
		int c = shade(shirt, 0.15);
		for (int x = 40; x < 48; x++) {
			for (int y = 0; y < 8; y++) {
				set(x, y, noisy(c, 6));
			}
		}
		for (int x = 32; x < 64; x++) {
			set(x, 8, c);
		}
		for (int x = 40; x < 48; x++) {
			set(x, 9, shade(c, -0.3)); // brim shadow on the front
		}
		set(43, 3, 0xF0F0F0); // button
	}

	// ---- pixels ----

	private void rect(int x, int y, int w, int h, int rgb, int noise) {
		for (int j = y; j < y + h; j++) {
			for (int i = x; i < x + w; i++) {
				set(i, j, noisy(rgb, noise));
			}
		}
	}

	private int noisy(int rgb, int amount) {
		if (amount <= 0) {
			return rgb;
		}
		int n = rnd.nextInt(amount * 2 + 1) - amount;
		return add(rgb, n);
	}

	private void set(int x, int y, int rgb) {
		img.setRGB(x, y, 0xFF000000 | (rgb & 0xFFFFFF));
	}

	private int get(int x, int y) {
		return img.getRGB(x, y) & 0xFFFFFF;
	}

	static int add(int rgb, int n) {
		int r = clamp((rgb >> 16 & 0xFF) + n);
		int g = clamp((rgb >> 8 & 0xFF) + n);
		int b = clamp((rgb & 0xFF) + n);
		return r << 16 | g << 8 | b;
	}

	static int shade(int rgb, double f) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		if (f >= 0) {
			r += (int) ((255 - r) * f);
			g += (int) ((255 - g) * f);
			b += (int) ((255 - b) * f);
		} else {
			r = (int) (r * (1 + f));
			g = (int) (g * (1 + f));
			b = (int) (b * (1 + f));
		}
		return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
	}

	static int blend(int a, int b, double t) {
		int r = (int) ((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
		int g = (int) ((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return r << 16 | g << 8 | bl;
	}

	static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}
}
