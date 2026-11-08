package me.sjun.richtextcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;

import me.sjun.richtextcraft.Composition;

/**
 * RichTextCraft: OS input methods in every text widget (docs/spec.md). The state lives here; the
 * mixins feed it SDL events and draw what it holds.
 */
public final class RichTextCraftClient implements ClientModInitializer {
	public static final String MOD_ID = "richtextcraft";

	/** The composition, with the widget it belongs to: a {@link MarkedTextHost}. */
	private static final Composition<Object> COMPOSITION = new Composition<>();

	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
				// Before the click reaches a widget: vanilla runs a button's action before it moves
				// focus, so Done must already see the committed text (spec §5.3).
				ScreenMouseEvents.beforeMouseClick(screen).register((s, event) -> commitOnFocusLoss()));
	}

	public static Composition<Object> composition() {
		return COMPOSITION;
	}

	/**
	 * Commits the marked text into the widget it belongs to and makes the input method forget it
	 * (spec §5). Does nothing when nothing is composing.
	 */
	public static void commitOnFocusLoss() {
		Object owner = COMPOSITION.owner();
		if (owner == null) {
			return;
		}
		String text = COMPOSITION.focusLost(Composition.FocusLoss.COMMIT);
		if (text != null && owner instanceof MarkedTextHost host) {
			host.richtextcraft$commit(text);
		}
		Ime.forget(Minecraft.getInstance().getWindow().handle());
	}
}
