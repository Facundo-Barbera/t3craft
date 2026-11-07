package dev.t3craft.client.workstation;

import java.util.List;

/**
 * The rendered content of one workstation screen: lines with their colour, the status and the ARGB
 * background. Pure value; the renderer turns it into one TextDisplay component.
 */
public record StationScreen(List<Line> lines, StationStatus status, int background) {
	public StationScreen {
		lines = List.copyOf(lines);
	}

	/** One line of text in 0xRRGGBB. */
	public record Line(String text, int color) {
	}

	/** All lines joined with '\n' (tests and logs). */
	public String plain() {
		StringBuilder b = new StringBuilder();
		for (Line l : lines) {
			if (b.length() > 0) {
				b.append('\n');
			}
			b.append(l.text());
		}
		return b.toString();
	}
}
