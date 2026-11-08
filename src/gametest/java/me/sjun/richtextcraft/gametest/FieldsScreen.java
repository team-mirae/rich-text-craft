package me.sjun.richtextcraft.gametest;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Two text fields, a multi-line field and a Done button that reads the first field when clicked,
 * as Add Server or a command block's Done does. For {@link CompositionGameTest}.
 */
public class FieldsScreen extends Screen {
	static final int X = 20;
	static final int WIDTH = 160;
	static final int HEIGHT = 20;
	static final int FIRST_Y = 30;
	static final int SECOND_Y = 60;
	static final int PAGE_Y = 90;
	static final int PAGE_HEIGHT = 60;
	static final int BUTTON_Y = 160;

	EditBox first;
	EditBox second;
	MultiLineEditBox page;
	/** The first field's value when Done was clicked; null until it is. */
	String done;
	/** The first field's value when the screen was removed; null until it is. */
	String removed;

	public FieldsScreen() {
		super(Component.literal("RichTextCraft test fields"));
	}

	@Override
	protected void init() {
		first = addRenderableWidget(new EditBox(font, X, FIRST_Y, WIDTH, HEIGHT, Component.literal("first")));
		second = addRenderableWidget(new EditBox(font, X, SECOND_Y, WIDTH, HEIGHT, Component.literal("second")));
		page = addRenderableWidget(MultiLineEditBox.builder().setX(X).setY(PAGE_Y)
				.build(font, WIDTH, PAGE_HEIGHT, Component.literal("page")));
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> done = first.getValue())
				.bounds(X, BUTTON_Y, WIDTH, HEIGHT).build());
		setInitialFocus(first);
	}

	@Override
	public void removed() {
		removed = first.getValue();
	}
}
