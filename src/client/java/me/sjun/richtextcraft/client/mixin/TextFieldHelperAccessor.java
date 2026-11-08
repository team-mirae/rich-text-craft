package me.sjun.richtextcraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.font.TextFieldHelper;

/** For drawing marked text on a sign. */
@Mixin(TextFieldHelper.class)
public interface TextFieldHelperAccessor {
	@Accessor("cursorPos")
	void richtextcraft$setCursorPos(int pos);

	@Accessor("selectionPos")
	void richtextcraft$setSelectionPos(int pos);
}
