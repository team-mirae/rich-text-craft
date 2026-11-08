package me.sjun.richtextcraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.TextInputManager;

import me.sjun.richtextcraft.client.RichTextCraftClient;

/**
 * Focus loss commits (spec §5). Every way a widget stops owning text input ends here: a widget
 * losing focus, {@code Gui.setScreen} (before the old screen's {@code removed()}), or another
 * widget taking text input over.
 */
@Mixin(TextInputManager.class)
public abstract class TextInputManagerMixin {
	@Inject(method = "stopTextInput()V", at = @At("HEAD"))
	private void richtextcraft$commitOnStop(CallbackInfo ci) {
		RichTextCraftClient.commitOnFocusLoss();
	}

	@Inject(method = "startTextInput", at = @At("HEAD"))
	private void richtextcraft$commitOnHandOver(Object newOwner, CallbackInfo ci) {
		if (RichTextCraftClient.composition().owner() != null && RichTextCraftClient.composition().owner() != newOwner) {
			RichTextCraftClient.commitOnFocusLoss();
		}
	}
}
