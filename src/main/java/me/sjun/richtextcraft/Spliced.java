package me.sjun.richtextcraft;

/**
 * A field's text as it is drawn while something is composing: the value with the marked text in
 * place of the selection, at the caret. Only ever drawn, never stored in the field (spec §2).
 *
 * @param text the value with the marked text spliced in
 * @param markStart where the marked text starts in {@link #text}
 * @param markEnd where it ends
 * @param caret the caret in {@link #text}: the input method's caret inside the marked text
 * @param segmentStart the converting segment in {@link #text}; equal to segmentEnd if none
 * @param segmentEnd its end
 */
public record Spliced(String text, int markStart, int markEnd, int caret, int segmentStart, int segmentEnd) {
	/**
	 * Splices {@code marked} into {@code value} in place of the selection between {@code cursor} and
	 * {@code anchor} (equal when nothing is selected), which the commit will replace.
	 */
	public static Spliced of(String value, int cursor, int anchor, MarkedText marked) {
		int from = Math.clamp(Math.min(cursor, anchor), 0, value.length());
		int to = Math.clamp(Math.max(cursor, anchor), 0, value.length());
		String text = value.substring(0, from) + marked.text() + value.substring(to);
		return new Spliced(text, from, from + marked.text().length(), from + marked.caret(),
				from + marked.segmentStart(), from + marked.segmentEnd());
	}

	public boolean hasSegment() {
		return segmentEnd > segmentStart;
	}
}
