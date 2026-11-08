package me.sjun.richtextcraft;

/**
 * What the input method is composing: its text, the caret inside it, and the segment it is
 * converting (the focused clause in Japanese or Chinese), if any. Indexes are Java {@code char}
 * offsets into {@link #text}; {@code segmentStart == segmentEnd} means no segment.
 *
 * <p>Never part of a field's value: a field shows it at its caret until the input method commits
 * (spec §2).
 */
public record MarkedText(String text, int caret, int segmentStart, int segmentEnd) {
	public MarkedText {
		if (text == null || text.isEmpty()) {
			throw new IllegalArgumentException("marked text is empty");
		}
		if (caret < 0 || caret > text.length()
				|| segmentStart < 0 || segmentEnd < segmentStart || segmentEnd > text.length()) {
			throw new IllegalArgumentException("bad offsets for \"" + text + "\": caret " + caret
					+ ", segment " + segmentStart + ".." + segmentEnd);
		}
	}

	/**
	 * Reads an SDL {@code SDL_EVENT_TEXT_EDITING}: {@code start} and {@code length} count code
	 * points. A negative start puts the caret at the end; a positive length marks the segment that
	 * starts at the caret. Returns null for empty text, which ends the composition.
	 */
	public static MarkedText fromSdl(String text, int start, int length) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		int codePoints = text.codePointCount(0, text.length());
		int caretCp = start < 0 ? codePoints : Math.min(start, codePoints);
		int caret = text.offsetByCodePoints(0, caretCp);
		if (length <= 0) {
			return new MarkedText(text, caret, caret, caret);
		}
		int end = text.offsetByCodePoints(0, Math.min(caretCp + length, codePoints));
		return new MarkedText(text, caret, caret, end);
	}

	/**
	 * Reads the game's own {@code PreeditEvent} (full text, caret, blocks, focused block): the
	 * focused block is the segment when there are several blocks.
	 */
	public static MarkedText fromBlocks(String text, int caret, java.util.List<String> blocks, int focusedBlock) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		caret = Math.clamp(caret, 0, text.length());
		if (blocks.size() <= 1) {
			return new MarkedText(text, caret, caret, caret);
		}
		int start = 0;
		for (int i = 0; i < focusedBlock; i++) {
			start += blocks.get(i).length();
		}
		int end = Math.min(text.length(), start + blocks.get(focusedBlock).length());
		return new MarkedText(text, caret, Math.min(start, end), end);
	}

	public boolean hasSegment() {
		return segmentEnd > segmentStart;
	}
}
