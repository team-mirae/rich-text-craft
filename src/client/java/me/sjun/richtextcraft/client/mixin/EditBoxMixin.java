package me.sjun.richtextcraft.client.mixin;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.TextInputManager;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import me.sjun.richtextcraft.MarkedText;
import me.sjun.richtextcraft.Spliced;
import me.sjun.richtextcraft.client.MarkedTextHost;
import me.sjun.richtextcraft.client.RichTextCraftClient;
import me.sjun.richtextcraft.client.Underline;

/**
 * Marked text inline in single-line fields (spec §2, §3). While composing, the field's state is
 * swapped for the spliced text for the length of one draw and put back afterwards, so the field's
 * own drawing (scrolling, centring, caret) does the layout. No listener ever sees the swap.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin implements MarkedTextHost {
	@Shadow @Final private Font font;
	@Shadow private String value;
	@Shadow private int maxLength;
	@Shadow private int displayPos;
	@Shadow private int cursorPos;
	@Shadow private int highlightPos;
	@Shadow private @Nullable String suggestion;
	@Shadow private int textColor;
	@Shadow private int textColorUneditable;
	@Shadow private boolean isEditable;
	@Shadow private int textX;
	@Shadow private int textY;

	@Shadow public abstract void insertText(String input);
	@Shadow public abstract int getInnerWidth();
	@Shadow public abstract boolean isVisible();
	@Shadow private void scrollTo(int pos) {}
	@Shadow private void updateTextPosition() {}
	@Shadow private FormattedCharSequence applyFormat(String text, int offset) { return null; }

	/** The spliced text being drawn, or null outside a composing draw. */
	@Unique private @Nullable Spliced richtextcraft$spliced;
	/** How far text after the marked text moved: its length minus the selection it replaces. */
	@Unique private int richtextcraft$shift;
	@Unique private boolean richtextcraft$formatting;
	@Unique private String richtextcraft$value;
	@Unique private int richtextcraft$maxLength;
	@Unique private int richtextcraft$displayPos;
	@Unique private int richtextcraft$cursorPos;
	@Unique private int richtextcraft$highlightPos;
	@Unique private @Nullable String richtextcraft$suggestion;

	@Override
	public void richtextcraft$commit(String text) {
		if (isEditable) {
			insertText(text);
		}
	}

	/** No pop-up bubble: the marked text is drawn inline (spec §2.6). */
	@Inject(method = "preeditUpdated", at = @At("HEAD"), cancellable = true)
	private void richtextcraft$noBubble(@Nullable PreeditEvent event, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}

	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
	private void richtextcraft$splice(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		MarkedText marked = RichTextCraftClient.composition().markedFor(this);
		if (marked == null || !isVisible()) {
			return;
		}
		Spliced spliced = Spliced.of(value, cursorPos, highlightPos, marked);
		richtextcraft$spliced = spliced;
		richtextcraft$shift = spliced.text().length() - value.length();
		richtextcraft$value = value;
		richtextcraft$maxLength = maxLength;
		richtextcraft$displayPos = displayPos;
		richtextcraft$cursorPos = cursorPos;
		richtextcraft$highlightPos = highlightPos;
		richtextcraft$suggestion = suggestion;

		value = spliced.text();
		// Draws the caret as a bar inside the text, never as the end-of-text underscore.
		maxLength = 0;
		suggestion = null;
		// Keep all of the marked text in view if it fits, and the caret always.
		scrollTo(spliced.markEnd());
		scrollTo(spliced.markStart());
		scrollTo(spliced.caret());
		cursorPos = spliced.caret();
		highlightPos = spliced.caret();
		updateTextPosition();
	}

	@Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
	private void richtextcraft$restore(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		Spliced spliced = richtextcraft$spliced;
		if (spliced == null) {
			return;
		}
		String displayed = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
		Underline.draw(graphics, font, spliced, displayed, displayPos, textX, textY, isEditable ? textColor : textColorUneditable);

		value = richtextcraft$value;
		maxLength = richtextcraft$maxLength;
		displayPos = richtextcraft$displayPos;
		cursorPos = richtextcraft$cursorPos;
		highlightPos = richtextcraft$highlightPos;
		suggestion = richtextcraft$suggestion;
		richtextcraft$spliced = null;
		updateTextPosition();
	}

	/**
	 * Candidate window under the start of the converting segment, or of the marked text (spec §3).
	 */
	@Redirect(method = "extractWidgetRenderState",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextInputManager;setTextInputArea(IIII)V"))
	private void richtextcraft$area(TextInputManager manager, int x0, int y0, int x1, int y1) {
		Spliced spliced = richtextcraft$spliced;
		if (spliced == null) {
			manager.setTextInputArea(x0, y0, x1, y1);
			return;
		}
		int anchor = spliced.hasSegment() ? spliced.segmentStart() : spliced.markStart();
		int x = textX + font.width(value.substring(displayPos, Math.max(displayPos, anchor)));
		manager.setTextInputArea(x, y0, x + 1, y1);
	}

	/**
	 * Formatters (chat's command colours) see the text around the marked text at its true offsets;
	 * the marked text itself is plain (spec §2.7).
	 */
	@Inject(method = "applyFormat", at = @At("HEAD"), cancellable = true)
	private void richtextcraft$format(String text, int offset, CallbackInfoReturnable<FormattedCharSequence> cir) {
		Spliced spliced = richtextcraft$spliced;
		if (spliced == null || richtextcraft$formatting) {
			return;
		}
		richtextcraft$formatting = true;
		try {
			int end = offset + text.length();
			List<FormattedCharSequence> parts = new ArrayList<>(3);
			int a = Math.min(end, spliced.markStart());
			if (a > offset) {
				parts.add(applyFormat(text.substring(0, a - offset), offset));
			}
			int b = Math.max(offset, spliced.markStart());
			int c = Math.min(end, spliced.markEnd());
			if (c > b) {
				parts.add(FormattedCharSequence.forward(text.substring(b - offset, c - offset), Style.EMPTY));
			}
			int d = Math.max(offset, spliced.markEnd());
			if (end > d) {
				parts.add(applyFormat(text.substring(d - offset), d - richtextcraft$shift));
			}
			cir.setReturnValue(FormattedCharSequence.composite(parts));
		} finally {
			richtextcraft$formatting = false;
		}
	}
}
