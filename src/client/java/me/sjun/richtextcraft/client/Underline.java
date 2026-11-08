package me.sjun.richtextcraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import me.sjun.richtextcraft.Spliced;

/**
 * Draws the marked text's underline, as native text fields do: a thin line under all of it and a
 * thick one under the segment being converted, with a pixel's gap between the two so clauses read
 * apart (spec §2.2).
 */
public final class Underline {
	private Underline() {}

	/**
	 * Underlines the part of {@code spliced} that falls on one drawn line.
	 *
	 * @param line the drawn line's text, which starts at {@code lineStart} in spliced.text()
	 * @param x where the line is drawn
	 * @param top the line's top; the underline sits on the glyphs' bottom row (top + 9)
	 */
	public static void draw(GuiGraphicsExtractor graphics, Font font, Spliced spliced, String line, int lineStart,
			int x, int top, int color) {
		int lineEnd = lineStart + line.length();
		int y = top + font.lineHeight;
		span(graphics, font, line, lineStart, lineEnd, spliced.markStart(), spliced.markEnd(), x, y, 1, color);
		if (spliced.hasSegment()) {
			span(graphics, font, line, lineStart, lineEnd, spliced.segmentStart(), spliced.segmentEnd(), x, y, 2, color);
		}
	}

	private static void span(GuiGraphicsExtractor graphics, Font font, String line, int lineStart, int lineEnd,
			int from, int to, int x, int y, int thickness, int color) {
		int a = Math.max(from, lineStart);
		int b = Math.min(to, lineEnd);
		if (a >= b) {
			return;
		}
		int x0 = x + font.width(line.substring(0, a - lineStart));
		int x1 = x + font.width(line.substring(0, b - lineStart));
		// Leave a pixel at each end of a segment so neighbouring clauses don't run together.
		if (thickness > 1) {
			x0 += 1;
			x1 -= 1;
		}
		if (x1 > x0) {
			graphics.fill(x0, y, x1, y + thickness, color);
		}
	}
}
