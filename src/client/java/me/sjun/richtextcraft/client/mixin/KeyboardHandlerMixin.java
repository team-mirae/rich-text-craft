package me.sjun.richtextcraft.client.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;

import me.sjun.richtextcraft.Composition;
import me.sjun.richtextcraft.MarkedText;
import me.sjun.richtextcraft.client.MarkedTextHost;
import me.sjun.richtextcraft.client.RichTextCraftClient;

/**
 * Where SDL's text events and key presses reach the game: keeps the composition (spec §2) and holds
 * keys back while composing (spec §4).
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	public abstract void textEditing(long handle, @Nullable PreeditEvent event);

	@Inject(method = "textEditing", at = @At("HEAD"))
	private void richtextcraft$edit(long handle, @Nullable PreeditEvent event, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (handle == 0L || handle != minecraft.getWindow().handle()) {
			return;
		}
		Object owner = ((TextInputManagerAccessor) minecraft.textInputManager()).richtextcraft$owner();
		Composition<Object> composition = RichTextCraftClient.composition();
		if (event == null || !(owner instanceof MarkedTextHost)) {
			composition.edit(null, null);
			return;
		}
		composition.edit(owner, MarkedText.fromBlocks(event.fullText(), event.caretPosition(), event.blocks(), event.focusedBlock()));
	}

	@Inject(method = "textInput", at = @At("HEAD"), cancellable = true)
	private void richtextcraft$commit(long handle, String text, CallbackInfo ci) {
		if (handle == 0L || handle != Minecraft.getInstance().getWindow().handle()) {
			return;
		}
		if (!RichTextCraftClient.composition().committed(text)) {
			// The input method committing again what focus loss already committed (spec §5.4).
			textEditing(handle, null);
			ci.cancel();
		}
	}

	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void richtextcraft$holdKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
		if (handle == 0L || handle != Minecraft.getInstance().getWindow().handle()) {
			return;
		}
		Composition<Object> composition = RichTextCraftClient.composition();
		boolean release = action == 0;
		if (composition.holdsKey(event.keycode(), release)) {
			ci.cancel();
		} else if (!release) {
			composition.keyPressed();
		}
	}
}
