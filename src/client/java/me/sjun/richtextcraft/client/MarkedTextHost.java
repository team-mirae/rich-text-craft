package me.sjun.richtextcraft.client;

/**
 * A widget that shows marked text inline: {@code EditBox}, {@code MultiLineEditBox} and the sign
 * editor, through their mixins. The game's own pop-up preedit bubble is off for these.
 */
public interface MarkedTextHost {
	/** Inserts text the input method composed, as if typed, replacing any selection. */
	void richtextcraft$commit(String text);
}
