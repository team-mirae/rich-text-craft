package me.sjun.richtextcraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.MultilineTextField;

/** For drawing marked text in {@code MultiLineEditBox} without telling its listeners. */
@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
	@Accessor("value")
	void richtextcraft$setValue(String value);

	@Accessor("cursor")
	void richtextcraft$setCursor(int cursor);

	@Accessor("selectCursor")
	int richtextcraft$selectCursor();

	@Accessor("selectCursor")
	void richtextcraft$setSelectCursor(int selectCursor);

	@Accessor("width")
	int richtextcraft$width();

	/** Rewraps the lines without running the value or cursor listeners. */
	@Invoker("reflowDisplayLines")
	void richtextcraft$reflow();
}
