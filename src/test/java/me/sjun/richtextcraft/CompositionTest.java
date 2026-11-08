package me.sjun.richtextcraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CompositionTest {
	private static final int ENTER = 0x0d;
	private static final int ESCAPE = 0x1b;
	private static final int BACKSPACE = 0x08;
	private static final int SPACE = 0x20;
	private static final int TAB = 0x09;
	private static final int LEFT = 0x40000050;
	private static final int F2 = 0x4000003b;
	private static final int F11 = 0x40000044;
	private static final int LSHIFT = 0x400000e1;

	private final Object field = new Object();
	private final Object other = new Object();

	// --- MarkedText from SDL (start/length count code points) ---

	@Test
	void sdlCaretAtEndWhenStartIsNegative() {
		MarkedText m = MarkedText.fromSdl("にほん", -1, 0);
		assertEquals(3, m.caret());
		assertFalse(m.hasSegment());
	}

	@Test
	void sdlSegmentStartsAtTheCaret() {
		MarkedText m = MarkedText.fromSdl("日本語を", 2, 2);
		assertEquals(2, m.caret());
		assertEquals(2, m.segmentStart());
		assertEquals(4, m.segmentEnd());
	}

	@Test
	void sdlOffsetsCountCodePointsNotChars() {
		// 𠮷 is two chars; the caret after it is char 2.
		MarkedText m = MarkedText.fromSdl("𠮷野家", 1, 2);
		assertEquals(2, m.caret());
		assertEquals(4, m.segmentEnd());
	}

	@Test
	void sdlOffsetsPastTheEndAreClamped() {
		MarkedText m = MarkedText.fromSdl("한", 5, 9);
		assertEquals(1, m.caret());
		assertFalse(m.hasSegment());
	}

	@Test
	void emptyEditIsNoComposition() {
		assertNull(MarkedText.fromSdl("", 0, 0));
		assertNull(MarkedText.fromSdl(null, 0, 0));
	}

	@Test
	void blocksGiveTheFocusedSegment() {
		MarkedText m = MarkedText.fromBlocks("今日は晴れ", 3, List.of("今日は", "晴れ"), 1);
		assertEquals(3, m.segmentStart());
		assertEquals(5, m.segmentEnd());
		MarkedText single = MarkedText.fromBlocks("ㅎ", 1, List.of("ㅎ"), 0);
		assertFalse(single.hasSegment());
	}

	// --- Spliced ---

	@Test
	void splicesAtTheCaret() {
		Spliced s = Spliced.of("ab", 1, 1, MarkedText.fromSdl("한", -1, 0));
		assertEquals("a한b", s.text());
		assertEquals(1, s.markStart());
		assertEquals(2, s.markEnd());
		assertEquals(2, s.caret());
	}

	@Test
	void replacesTheSelectionEitherWay() {
		MarkedText m = MarkedText.fromSdl("x", -1, 0);
		assertEquals("axd", Spliced.of("abcd", 1, 3, m).text());
		assertEquals("axd", Spliced.of("abcd", 3, 1, m).text());
	}

	@Test
	void caretAndSegmentMoveWithTheMarkedText() {
		Spliced s = Spliced.of("> ", 2, 2, MarkedText.fromSdl("きょうは", 0, 3));
		assertEquals(2, s.caret());
		assertEquals(2, s.segmentStart());
		assertEquals(5, s.segmentEnd());
	}

	// --- Composition ---

	@Test
	void marksBelongToOneWidget() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("ㅎ", -1, 0));
		assertEquals("ㅎ", c.markedFor(field).text());
		assertNull(c.markedFor(other));
		assertSame(field, c.owner());
	}

	@Test
	void emptyEditCancels() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("ㅎ", -1, 0));
		c.edit(field, null);
		assertFalse(c.isComposing());
		assertNull(c.owner());
	}

	@Test
	void commitEndsTheComposition() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("한", -1, 0));
		assertTrue(c.committed("한"));
		assertFalse(c.isComposing());
	}

	@Test
	void focusLossCommitsOrCancels() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("한", -1, 0));
		assertEquals("한", c.focusLost(Composition.FocusLoss.COMMIT));
		assertFalse(c.isComposing());
		c.edit(field, MarkedText.fromSdl("한", -1, 0));
		assertNull(c.focusLost(Composition.FocusLoss.CANCEL));
		assertNull(c.focusLost(Composition.FocusLoss.COMMIT), "nothing composing");
	}

	@Test
	void theInputMethodCommittingAgainIsDropped() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("한", -1, 0));
		c.focusLost(Composition.FocusLoss.COMMIT);
		assertFalse(c.committed("한"), "echo of the focus-loss commit");
		assertTrue(c.committed("한"), "only once");
	}

	@Test
	void echoGuardEndsWithAKeyOrNewComposition() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("한", -1, 0));
		c.focusLost(Composition.FocusLoss.COMMIT);
		c.keyPressed();
		assertTrue(c.committed("한"));

		c.edit(field, MarkedText.fromSdl("한", -1, 0));
		c.focusLost(Composition.FocusLoss.COMMIT);
		c.edit(other, MarkedText.fromSdl("하", -1, 0));
		assertTrue(c.committed("한"));
	}

	@Test
	void differentTextIsNotAnEcho() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("한", -1, 0));
		c.focusLost(Composition.FocusLoss.COMMIT);
		assertTrue(c.committed("a"));
	}

	// --- Keys (spec §4) ---

	@Test
	void keysPassWhenNotComposing() {
		Composition<Object> c = new Composition<>();
		for (int key : new int[] {ENTER, ESCAPE, BACKSPACE, SPACE, TAB, LEFT}) {
			assertFalse(c.holdsKey(key, false));
		}
	}

	@Test
	void composingHoldsEditingKeys() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("にほん", -1, 0));
		for (int key : new int[] {ENTER, ESCAPE, BACKSPACE, SPACE, TAB, LEFT, 'a'}) {
			assertTrue(c.holdsKey(key, false), "key " + Integer.toHexString(key));
		}
	}

	@Test
	void releasesFunctionKeysAndModifiersAlwaysPass() {
		Composition<Object> c = new Composition<>();
		c.edit(field, MarkedText.fromSdl("にほん", -1, 0));
		assertFalse(c.holdsKey(ENTER, true));
		assertFalse(c.holdsKey(F2, false));
		assertFalse(c.holdsKey(F11, false));
		assertFalse(c.holdsKey(LSHIFT, false));
	}
}
