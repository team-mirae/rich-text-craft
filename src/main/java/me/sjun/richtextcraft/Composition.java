package me.sjun.richtextcraft;

import java.util.Objects;

/**
 * The OS input method's composition, with the text widget it belongs to (spec §2, §4, §5). One at
 * a time: the OS composes into one window, and the game has one text-input owner.
 *
 * <p>No game code here, so it can be unit-tested. The client feeds it SDL events and asks it what
 * to draw, which keys to hold back and what to do on focus loss.
 *
 * @param <O> the widget type (any object: a text field, or a screen that edits text itself)
 */
public final class Composition<O> {
	/** What focus loss does with the marked text (spec §5). */
	public enum FocusLoss {
		/** Insert it into the widget that loses focus, as macOS and Windows native fields do. */
		COMMIT,
		/** Drop it. */
		CANCEL
	}

	private O owner;
	private MarkedText marked;
	/**
	 * Text the mod committed on focus loss, in case the input method commits it again (spec §5.4);
	 * null when there is none.
	 */
	private String echo;

	/** The marked text for {@code widget}, or null if it isn't composing. */
	public MarkedText markedFor(Object widget) {
		return widget != null && widget == owner ? marked : null;
	}

	public boolean isComposing() {
		return marked != null;
	}

	public O owner() {
		return marked != null ? owner : null;
	}

	/** An SDL text-editing event for {@code widget}: null or empty text ends the composition. */
	public void edit(O widget, MarkedText text) {
		if (text == null || widget == null) {
			clear();
			return;
		}
		// New composing: a later commit is new text, not an echo (macOS sends no key event here).
		echo = null;
		owner = widget;
		marked = text;
	}

	/**
	 * An SDL text-input event: the input method committed. The game delivers the committed text
	 * itself, so this only ends the composition. Returns false if the text is the input method
	 * committing again what focus loss already committed (spec §5.4): drop it.
	 */
	public boolean committed(String text) {
		boolean echoed = echo != null && echo.equals(text);
		echo = null;
		clear();
		return !echoed;
	}

	/** Any key press: a later text input is typed, not an echo. */
	public void keyPressed() {
		echo = null;
	}

	/**
	 * The owner is losing focus or the screen is closing. Returns the text to insert into it first
	 * ({@link FocusLoss#COMMIT}), or null; either way the composition ends.
	 */
	public String focusLost(FocusLoss policy) {
		String text = marked != null && policy == FocusLoss.COMMIT ? marked.text() : null;
		clear();
		echo = text;
		return text;
	}

	public void clear() {
		owner = null;
		marked = null;
	}

	/**
	 * Whether a key press belongs to the input method rather than the widget (spec §4): any press
	 * or repeat while composing, except function keys and lone modifiers, which input methods don't
	 * use. Key releases always pass, so the game never sees a key stuck down.
	 *
	 * @param keycode the SDL keycode
	 * @param release whether this is a release
	 */
	public boolean holdsKey(int keycode, boolean release) {
		return marked != null && !release && !isFunctionKey(keycode) && !isModifier(keycode);
	}

	/** SDLK_F1–F12 and SDLK_F13–F24. */
	static boolean isFunctionKey(int keycode) {
		return keycode >= 0x4000003a && keycode <= 0x40000045 || keycode >= 0x40000068 && keycode <= 0x40000073;
	}

	/** SDLK_LCTRL through SDLK_RGUI, and SDLK_CAPSLOCK. */
	static boolean isModifier(int keycode) {
		return keycode >= 0x400000e0 && keycode <= 0x400000e7 || keycode == 0x40000039;
	}

	@Override
	public String toString() {
		return marked == null ? "Composition[idle]" : "Composition[" + Objects.toString(owner) + ": " + marked + "]";
	}
}
