package me.sjun.richtextcraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.platform.TextInputManager;

@Mixin(TextInputManager.class)
public interface TextInputManagerAccessor {
	/** The widget (or sign screen) that owns OS text input; null when it's off. */
	@Accessor("owner")
	Object richtextcraft$owner();
}
