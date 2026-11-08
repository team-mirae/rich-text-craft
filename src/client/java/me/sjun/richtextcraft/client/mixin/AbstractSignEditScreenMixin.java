package me.sjun.richtextcraft.client.mixin;

import org.joml.Vector2f;
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

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import me.sjun.richtextcraft.MarkedText;
import me.sjun.richtextcraft.Spliced;
import me.sjun.richtextcraft.client.MarkedTextHost;
import me.sjun.richtextcraft.client.RichTextCraftClient;
import me.sjun.richtextcraft.client.Underline;

/**
 * Marked text inline on signs (spec §2). The sign screen owns text input itself; the current line
 * holds the spliced text for one draw of the sign's text, which isn't the sign in the world.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen implements MarkedTextHost {
	@Shadow @Final protected SignBlockEntity sign;
	@Shadow @Final private String[] messages;
	@Shadow @Final private TextFieldHelper signField;
	@Shadow @Final private int textColor;
	@Shadow private int line;

	@Unique private @Nullable Spliced richtextcraft$spliced;
	@Unique private String richtextcraft$message;
	@Unique private int richtextcraft$cursor;
	@Unique private int richtextcraft$selection;

	private AbstractSignEditScreenMixin(Component title) {
		super(title);
	}

	@Override
	public void richtextcraft$commit(String text) {
		signField.insertText(text);
	}

	/** No pop-up bubble: the marked text is drawn inline (spec §2.6). */
	@Inject(method = "preeditUpdated", at = @At("HEAD"), cancellable = true)
	private void richtextcraft$noBubble(@Nullable PreeditEvent event, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}

	@Inject(method = "extractSignText", at = @At("HEAD"))
	private void richtextcraft$splice(GuiGraphicsExtractor graphics, Vector2f cursorPosOutput, CallbackInfo ci) {
		MarkedText marked = RichTextCraftClient.composition().markedFor(this);
		if (marked == null) {
			return;
		}
		richtextcraft$message = messages[line];
		richtextcraft$cursor = signField.getCursorPos();
		richtextcraft$selection = signField.getSelectionPos();
		Spliced spliced = Spliced.of(richtextcraft$message, richtextcraft$cursor, richtextcraft$selection, marked);
		richtextcraft$spliced = spliced;
		messages[line] = spliced.text();
		TextFieldHelperAccessor field = (TextFieldHelperAccessor) signField;
		field.richtextcraft$setCursorPos(spliced.caret());
		field.richtextcraft$setSelectionPos(spliced.caret());
	}

	@Inject(method = "extractSignText", at = @At("RETURN"))
	private void richtextcraft$restore(GuiGraphicsExtractor graphics, Vector2f cursorPosOutput, CallbackInfo ci) {
		Spliced spliced = richtextcraft$spliced;
		if (spliced == null) {
			return;
		}
		int lineHeight = sign.getTextLineHeight();
		int top = line * lineHeight - 4 * lineHeight / 2;
		Underline.draw(graphics, font, spliced, spliced.text(), 0, -font.width(spliced.text()) / 2, top, textColor);

		messages[line] = richtextcraft$message;
		TextFieldHelperAccessor field = (TextFieldHelperAccessor) signField;
		field.richtextcraft$setCursorPos(richtextcraft$cursor);
		field.richtextcraft$setSelectionPos(richtextcraft$selection);
		richtextcraft$spliced = null;
	}

	/** While composing, the caret is a bar even at the end of the text, never an underscore (spec §2.2). */
	@Redirect(method = "extractSignText",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
	private void richtextcraft$barCaret(GuiGraphicsExtractor graphics, Font font, int x, int y, int color, boolean shadow) {
		if (richtextcraft$spliced != null) {
			TextCursorUtils.extractInsertCursor(graphics, x, y, ARGB.opaque(color), sign.getTextLineHeight());
		} else {
			TextCursorUtils.extractAppendCursor(graphics, font, x, y, color, shadow);
		}
	}
}
