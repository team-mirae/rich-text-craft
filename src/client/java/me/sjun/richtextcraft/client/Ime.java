package me.sjun.richtextcraft.client;

import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Platform;
import org.lwjgl.system.macosx.ObjCRuntime;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Makes the OS input method drop its composition after the mod committed it itself (spec §5.4).
 * SDL has no call that does this everywhere: {@code SDL_ClearComposition} only works on Windows.
 */
final class Ime {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static boolean macFailed;

	private Ime() {}

	/** Drops the composition for {@code window} (an SDL window handle), leaving text input on. */
	static void forget(long window) {
		switch (Platform.get()) {
			case WINDOWS -> SDLKeyboard.SDL_ClearComposition(window);
			case MACOSX -> discardMarkedText();
			default -> {
				// X11 resets the input context (and frees its text); Wayland and IBus/Fcitx reset too.
				if (SDLKeyboard.SDL_TextInputActive(window)) {
					SDLKeyboard.SDL_StopTextInput(window);
					SDLKeyboard.SDL_StartTextInput(window);
				}
			}
		}
	}

	/** {@code [[NSTextInputContext currentInputContext] discardMarkedText]}, on the main thread. */
	private static void discardMarkedText() {
		if (macFailed) {
			return;
		}
		try {
			long msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
			long contextClass = ObjCRuntime.objc_getClass("NSTextInputContext");
			long context = JNI.invokePPP(contextClass, ObjCRuntime.sel_getUid("currentInputContext"), msgSend);
			if (context != 0) {
				JNI.invokePPP(context, ObjCRuntime.sel_getUid("discardMarkedText"), msgSend);
			}
		} catch (Throwable t) {
			macFailed = true;
			LOGGER.warn("RichTextCraft: couldn't ask the input method to drop its marked text", t);
		}
	}
}
