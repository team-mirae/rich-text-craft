package me.sjun.richtextcraft.gametest;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextInputManager;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import me.sjun.richtextcraft.client.RichTextCraftClient;

/**
 * The mod in the real game, with SDL's text events injected where SDL's event handler delivers
 * them ({@code KeyboardHandler.textEditing} and {@code textInput}) and keys pressed through the
 * game's own key handler. Run with {@code ./gradlew runClientGameTest}; screenshots land in
 * build/run/clientGameTest/screenshots.
 */
public class CompositionGameTest implements FabricClientGameTest {
	// SDL scancodes.
	private static final int ENTER = 40;
	private static final int ESCAPE = 41;
	private static final int BACKSPACE = 42;
	private static final int TAB = 43;
	private static final int SPACE = 44;
	private static final int LEFT = 80;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();

			chatKeepsMarkedTextOutOfTheValue(context);
			chatHoldsKeysWhileComposing(context);
			chatEscapeCancelsTheCompositionNotTheScreen(context);
			commitReplacesTheSelection(context);
			doneSeesTheCommittedText(context);
			clickingAnotherFieldCommitsIntoTheFirst(context);
			tabCommitsIntoTheFirst(context);
			closingTheScreenCommits(context);
			multiLineFieldComposes(context);
			candidateAreaFollowsTheSegment(context);
			signComposes(context, world);
			keysBehaveAsVanillaWhenNotComposing(context);
		}
	}

	// --- Chat (spec §2, §4) ---

	private void chatKeepsMarkedTextOutOfTheValue(ClientGameTestContext context) {
		openChat(context);
		commit(context, "hi ");
		edit(context, "にほんご", 1, 2);
		context.waitTick();
		context.takeScreenshot("richtextcraft-chat-segment");
		expect("chat value while composing", "hi ", chatValue(context));
		commit(context, "日本語");
		expect("chat value after the commit", "hi 日本語", chatValue(context));
		edit(context, "한", -1, 0);
		context.waitTick();
		context.takeScreenshot("richtextcraft-chat-korean");
		edit(context, null, 0, 0);
		expect("chat value after a cancel", "hi 日本語", chatValue(context));
		closeChat(context);
	}

	private void chatHoldsKeysWhileComposing(ClientGameTestContext context) {
		openChat(context);
		commit(context, "ab");
		edit(context, "かな", -1, 0);
		for (int key : new int[] {ENTER, BACKSPACE, LEFT, TAB, SPACE}) {
			context.getInput().pressKey(key);
		}
		context.waitTick();
		expectScreen(context, ChatScreen.class, "Enter while composing must not send");
		expect("keys while composing", "ab", chatValue(context));
		int caret = context.computeOnClient(client -> chatField(client).getCursorPosition());
		expect("caret while composing", 2, caret);

		commit(context, "仮名");
		context.getInput().pressKey(ENTER);
		context.waitTick();
		expectScreen(context, null, "Enter after the commit sends");
	}

	private void chatEscapeCancelsTheCompositionNotTheScreen(ClientGameTestContext context) {
		openChat(context);
		commit(context, "x");
		edit(context, "ㅎ", -1, 0);
		context.getInput().pressKey(ESCAPE);
		context.waitTick();
		expectScreen(context, ChatScreen.class, "Escape while composing must not close chat");
		// The input method cancels on Escape: an empty edit.
		edit(context, null, 0, 0);
		expect("value after cancel", "x", chatValue(context));
		context.getInput().pressKey(ESCAPE);
		context.waitForScreen(null);
	}

	// --- Commit (spec §2.4) ---

	private void commitReplacesTheSelection(ClientGameTestContext context) {
		FieldsScreen screen = openFields(context);
		context.runOnClient(client -> {
			screen.first.setValue("hello world");
			screen.first.moveCursorTo(6, false);
			screen.first.moveCursorTo(11, true);
		});
		edit(context, "せかい", -1, 0);
		context.waitTick();
		context.takeScreenshot("richtextcraft-field-over-selection");
		expect("value while composing over a selection", "hello world", value(context, screen));
		commit(context, "世界");
		expect("commit replaces the selection", "hello 世界", value(context, screen));
		closeFields(context);
	}

	// --- Focus loss commits (spec §5) ---

	private void doneSeesTheCommittedText(ClientGameTestContext context) {
		FieldsScreen screen = openFields(context);
		edit(context, "학교", -1, 0);
		click(context, FieldsScreen.BUTTON_Y);
		String done = context.computeOnClient(client -> screen.done);
		closeFields(context);
		expect("Done read", "학교", done);
		expectIdle(context);
	}

	private void clickingAnotherFieldCommitsIntoTheFirst(ClientGameTestContext context) {
		FieldsScreen screen = openFields(context);
		edit(context, "한글", -1, 0);
		click(context, FieldsScreen.SECOND_Y);
		commit(context, "hello");
		String first = context.computeOnClient(client -> screen.first.getValue());
		String second = context.computeOnClient(client -> screen.second.getValue());
		closeFields(context);
		expect("first field", "한글", first);
		expect("second field", "hello", second);
	}

	private void tabCommitsIntoTheFirst(ClientGameTestContext context) {
		// Tab while composing belongs to the input method; focus moved by the game commits.
		FieldsScreen screen = openFields(context);
		edit(context, "ねこ", -1, 0);
		context.runOnClient(client -> screen.setFocused(screen.second));
		context.waitTick();
		String first = context.computeOnClient(client -> screen.first.getValue());
		closeFields(context);
		expect("first field after focus moved", "ねこ", first);
	}

	private void closingTheScreenCommits(ClientGameTestContext context) {
		FieldsScreen screen = openFields(context);
		edit(context, "猫", -1, 0);
		closeFields(context);
		expect("value seen by removed()", "猫", context.computeOnClient(client -> screen.removed));
		// The input method committing the same text again is dropped (spec §5.4).
		expectIdle(context);
	}

	// --- Multi-line (spec §2.3) ---

	private void multiLineFieldComposes(ClientGameTestContext context) {
		FieldsScreen screen = openFields(context);
		context.runOnClient(client -> screen.setFocused(screen.page));
		commit(context, "The quick brown fox jumps over the lazy dog and ");
		edit(context, "かんじへんかん", 0, 4);
		context.getInput().pressKey(ENTER);
		context.waitTick();
		context.takeScreenshot("richtextcraft-multiline-segment");
		expect("page while composing", "The quick brown fox jumps over the lazy dog and ",
				context.computeOnClient(client -> screen.page.getValue()));
		commit(context, "漢字変換");
		expect("page after the commit", "The quick brown fox jumps over the lazy dog and 漢字変換",
				context.computeOnClient(client -> screen.page.getValue()));
		closeFields(context);
	}

	// --- Candidate window (spec §3) ---

	private void candidateAreaFollowsTheSegment(ClientGameTestContext context) {
		FieldsScreen screen = openFields(context);
		edit(context, "ab", -1, 0);
		context.waitTick();
		int atStart = areaX(context);
		edit(context, "ab", 1, 1);
		context.waitTick();
		int atSegment = areaX(context);
		commit(context, "ab");
		context.waitTick();
		int atCaret = areaX(context);
		closeFields(context);
		if (!(atStart < atSegment && atSegment < atCaret)) {
			throw new AssertionError("text input area x: marked text start " + atStart + ", segment " + atSegment
					+ ", caret after commit " + atCaret + "; expected increasing");
		}
	}

	// --- Signs ---

	private void signComposes(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos pos = context.computeOnClient(client -> client.player.blockPosition().offset(1, 0, 1));
		world.getServer().runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:oak_sign");
		context.waitFor(client -> client.level.getBlockEntity(pos) instanceof SignBlockEntity);
		context.setScreen(() -> new SignEditScreen((SignBlockEntity) Minecraft.getInstance().level.getBlockEntity(pos),
				SignTextSlot.FRONT, false));
		context.waitForScreen(SignEditScreen.class);
		context.waitTick();
		commit(context, "Hi ");
		edit(context, "看板", -1, 0);
		context.getInput().pressKey(ENTER);
		context.waitTick();
		context.takeScreenshot("richtextcraft-sign");
		Function<Minecraft, String> line0 = client -> ((SignBlockEntity) client.level.getBlockEntity(pos))
				.getText(SignTextSlot.FRONT).getMessages(false).get(0).getString();
		expect("sign line while composing (Enter held)", "Hi ", context.computeOnClient(line0::apply));
		context.setScreen(() -> null);
		context.waitForScreen(null);
		expect("sign line after the screen closed", "Hi 看板", context.computeOnClient(line0::apply));
	}

	// --- No regressions (spec §6) ---

	private void keysBehaveAsVanillaWhenNotComposing(ClientGameTestContext context) {
		openChat(context);
		commit(context, "abc");
		context.getInput().pressKey(BACKSPACE);
		context.getInput().pressKey(LEFT);
		commit(context, "X");
		context.waitTick();
		expect("vanilla editing", "aXb", chatValue(context));
		context.getInput().pressKey(ESCAPE);
		context.waitForScreen(null);
	}

	// --- Helpers ---

	/** As SDLEventHandler delivers SDL_EVENT_TEXT_EDITING. */
	private static void edit(ClientGameTestContext context, String text, int start, int length) {
		context.runOnClient(client -> client.keyboardHandler.textEditing(client.getWindow().handle(),
				PreeditEvent.fromSdlTextEditing(text, start, length)));
	}

	/** As SDLEventHandler delivers SDL_EVENT_TEXT_INPUT. */
	private static void commit(ClientGameTestContext context, String text) {
		context.runOnClient(client -> client.keyboardHandler.textInput(client.getWindow().handle(), text));
	}

	private static int areaX(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			try {
				Field field = TextInputManager.class.getDeclaredField("areaX");
				field.setAccessible(true);
				return field.getInt(client.textInputManager());
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	private static void expectIdle(ClientGameTestContext context) {
		boolean composing = context.computeOnClient(client -> RichTextCraftClient.composition().isComposing());
		if (composing) {
			throw new AssertionError("still composing: " + RichTextCraftClient.composition());
		}
	}

	private static void expect(String what, Object expected, Object actual) {
		if (!Objects.equals(expected, actual)) {
			throw new AssertionError(what + ": expected \"" + expected + "\", got \"" + actual + "\"");
		}
	}

	private static void expectScreen(ClientGameTestContext context, Class<? extends Screen> type, String what) {
		Screen screen = context.computeOnClient(client -> client.gui.screen());
		if (type == null ? screen != null : !type.isInstance(screen)) {
			throw new AssertionError(what + ": screen is " + screen);
		}
	}

	private static EditBox chatField(Minecraft client) {
		return (EditBox) client.gui.screen().getFocused();
	}

	private static String chatValue(ClientGameTestContext context) {
		return context.computeOnClient(client -> chatField(client).getValue());
	}

	private static String value(ClientGameTestContext context, FieldsScreen screen) {
		return context.computeOnClient(client -> screen.first.getValue());
	}

	private static void openChat(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);
		context.waitTick();
	}

	private static void closeChat(ClientGameTestContext context) {
		context.getInput().pressKey(ESCAPE);
		context.waitForScreen(null);
	}

	private static FieldsScreen openFields(ClientGameTestContext context) {
		context.setScreen(FieldsScreen::new);
		context.waitForScreen(FieldsScreen.class);
		context.waitTick();
		return context.computeOnClient(client -> (FieldsScreen) client.gui.screen());
	}

	private static void closeFields(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.waitForScreen(null);
	}

	/** Clicks the middle of the widget at GUI row {@code y} of {@link FieldsScreen}. */
	private static void click(ClientGameTestContext context, int y) {
		double[] at = context.computeOnClient(client -> {
			double scale = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
			return new double[] {(FieldsScreen.X + FieldsScreen.WIDTH / 2.0) * scale, (y + FieldsScreen.HEIGHT / 2.0) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}
}
