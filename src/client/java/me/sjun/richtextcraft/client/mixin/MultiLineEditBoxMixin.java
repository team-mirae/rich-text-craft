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
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import me.sjun.richtextcraft.MarkedText;
import me.sjun.richtextcraft.Spliced;
import me.sjun.richtextcraft.client.MarkedTextHost;
import me.sjun.richtextcraft.client.RichTextCraftClient;
import me.sjun.richtextcraft.client.Underline;

/**
 * Marked text inline in multi-line fields: book pages and dialog inputs (spec §2, §3). As in
 * {@link EditBoxMixin}, the text field holds the spliced text for one draw, rewrapped, and is put
 * back afterwards without running its listeners.
 */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin extends AbstractTextAreaWidget implements MarkedTextHost {
	@Shadow @Final private Font font;
	@Shadow @Final private MultilineTextField textField;
	@Shadow @Final private int textColor;

	@Shadow private void scrollToCursor() {}

	@Unique private @Nullable Spliced richtextcraft$spliced;
	@Unique private String richtextcraft$value;
	@Unique private int richtextcraft$cursor;
	@Unique private int richtextcraft$selectCursor;

	private MultiLineEditBoxMixin(int x, int y, int width, int height, Component narration,
			AbstractScrollArea.ScrollbarSettings settings) {
		super(x, y, width, height, narration, settings);
	}

	@Override
	public void richtextcraft$commit(String text) {
		textField.insertText(text);
	}

	/** No pop-up bubble: the marked text is drawn inline (spec §2.6). */
	@Inject(method = "preeditUpdated", at = @At("HEAD"), cancellable = true)
	private void richtextcraft$noBubble(@Nullable PreeditEvent event, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}

	@Inject(method = "extractContents", at = @At("HEAD"))
	private void richtextcraft$splice(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		MarkedText marked = RichTextCraftClient.composition().markedFor(this);
		if (marked == null) {
			return;
		}
		MultilineTextFieldAccessor field = (MultilineTextFieldAccessor) textField;
		richtextcraft$value = textField.value();
		richtextcraft$cursor = textField.cursor();
		richtextcraft$selectCursor = field.richtextcraft$selectCursor();
		Spliced spliced = Spliced.of(richtextcraft$value, richtextcraft$cursor, richtextcraft$selectCursor, marked);
		richtextcraft$spliced = spliced;

		field.richtextcraft$setValue(spliced.text());
		field.richtextcraft$setCursor(spliced.caret());
		field.richtextcraft$setSelectCursor(spliced.caret());
		field.richtextcraft$reflow();
		// Takes effect from the next frame: this one is already translated by the old scroll.
		scrollToCursor();
	}

	@Inject(method = "extractContents", at = @At("RETURN"))
	private void richtextcraft$restore(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		Spliced spliced = richtextcraft$spliced;
		if (spliced == null) {
			return;
		}
		List<int[]> lines = richtextcraft$lines(spliced.text());
		for (int i = 0; i < lines.size(); i++) {
			int[] line = lines.get(i);
			Underline.draw(graphics, font, spliced, spliced.text().substring(line[0], line[1]), line[0],
					getInnerLeft(), getInnerTop() + i * font.lineHeight, textColor);
		}

		MultilineTextFieldAccessor field = (MultilineTextFieldAccessor) textField;
		field.richtextcraft$setValue(richtextcraft$value);
		field.richtextcraft$setCursor(richtextcraft$cursor);
		field.richtextcraft$setSelectCursor(richtextcraft$selectCursor);
		field.richtextcraft$reflow();
		richtextcraft$spliced = null;
	}

	/**
	 * Candidate window under the start of the converting segment or the marked text, and in both
	 * cases where the line is on screen: vanilla forgets the scroll offset (spec §3).
	 */
	@Redirect(method = "extractContents",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/TextInputManager;setTextInputArea(IIII)V"))
	private void richtextcraft$area(TextInputManager manager, int x0, int y0, int x1, int y1) {
		int scroll = (int) Math.round(scrollAmount());
		Spliced spliced = richtextcraft$spliced;
		if (spliced == null) {
			manager.setTextInputArea(x0, y0 - scroll, x1, y1 - scroll);
			return;
		}
		int anchor = spliced.hasSegment() ? spliced.segmentStart() : spliced.markStart();
		List<int[]> lines = richtextcraft$lines(spliced.text());
		int row = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (anchor >= lines.get(i)[0] && anchor <= lines.get(i)[1]) {
				row = i;
				break;
			}
		}
		int lineStart = lines.isEmpty() ? 0 : lines.get(row)[0];
		int x = getInnerLeft() + font.width(spliced.text().substring(lineStart, Math.max(lineStart, anchor)));
		int y = getInnerTop() + row * font.lineHeight - scroll;
		manager.setTextInputArea(x, y, x + 1, y + font.lineHeight + 1);
	}

	/** The wrapped lines of {@code text} as [start, end) pairs, as the text field wraps them. */
	@Unique
	private List<int[]> richtextcraft$lines(String text) {
		List<int[]> lines = new ArrayList<>();
		font.getSplitter().splitLines(text, ((MultilineTextFieldAccessor) textField).richtextcraft$width(), Style.EMPTY, false,
				(style, start, end) -> lines.add(new int[] {start, end}));
		return lines;
	}

	/** While composing, the caret is a bar even at the end of the text, never an underscore (spec §2.2). */
	@Redirect(method = "extractContents",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
	private void richtextcraft$barCaret(GuiGraphicsExtractor graphics, Font font, int x, int y, int color, boolean shadow) {
		if (richtextcraft$spliced != null) {
			TextCursorUtils.extractInsertCursor(graphics, x, y, color, font.lineHeight + 1);
		} else {
			TextCursorUtils.extractAppendCursor(graphics, font, x, y, color, shadow);
		}
	}
}
