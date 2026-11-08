# Testing

## Automated

| Command | What it covers |
|---|---|
| `./gradlew test` | `CompositionTest`: SDL offsets (code points, surrogate pairs, clamping), splicing over a selection, commit and cancel, focus-loss commit, the echo guard, and which keys are held back. No game. |
| `./gradlew runClientGameTest` | `CompositionGameTest`: boots the real client, opens a world, and injects SDL text events at `KeyboardHandler.textEditing` and `textInput`, where `SDLEventHandler` delivers them. Keys go through the game's own key handler. Screenshots go to `build/run/clientGameTest/screenshots`. |

The game test checks:

- **Chat:** marked text isn't in the value. A commit lands at the caret. A cancel changes
  nothing.
- **Held keys:** Enter, Backspace, Left, Tab and Space while composing leave the value and the
  caret alone. Enter doesn't send. After the commit, Enter sends (the chat log shows the message).
- **Escape:** while composing, Escape doesn't close chat. After the input method's cancel, it
  does.
- **Selection:** a commit replaces the selection, and the selection stays while composing.
- **Focus loss (spec §5):**
  - Clicking Done: Done reads the committed text.
  - Clicking another field: the text is committed into the first field.
  - Focus moved by code: the text is committed.
  - Closing the screen: `removed()` sees the committed text.
  - Afterwards, nothing is still composing.
- **Multi-line field:** marked text wraps, Enter doesn't add a line, and the commit lands.
- **Candidate area (spec §3):** the text input area's x is at the start of the marked text, moves
  to the segment start, and then follows the caret after the commit.
- **Sign:** while composing, Enter doesn't change line and the sign's text doesn't change. Closing
  the screen commits into the sign.
- **No composition:** Backspace, Left and typing edit chat as in vanilla.

What the game test doesn't cover: SDL itself and the OS input methods. The events are injected
after SDL, so platform behaviour (what SDL sends, in what order, and the `Ime.forget` calls) is
only checked by hand, below. The macOS `discardMarkedText` call does run in the game test on
macOS (no input method attached) and logs no error.

## By hand, with real input methods

Run `./gradlew runClient`, open a world, and go through each check in windowed **and** fullscreen
mode (F11).

1. **Chat:** type a word that needs conversion. The marked text is underlined inline at the
   caret, with a thick underline under the converting clause. The candidate window sits under the
   clause.
2. While composing, **Enter** confirms without sending, **Escape** cancels without closing chat,
   **arrows** move between clauses, and **Backspace** deletes inside the marked text.
3. After the commit, Enter sends and Escape closes, as in vanilla.
4. Compose in the anvil, then click the output slot: the item gets the committed name.
5. Compose in the Add Server name field, then click Done: the name includes it.
6. Compose on a book page near the right edge: it wraps. Scroll a long page: the candidate
   window stays under the caret.
7. Compose on a sign, then press Done: the sign has the text.
8. Command block: compose in a command, then click another field. The committed text stays in
   the command field and isn't typed again into the new field.
9. Creative inventory search and recipe book search: results update only after the commit.
10. **Without an input method** (plain US layout): typing, Ctrl/Cmd shortcuts, chat history (Up),
    command suggestions (Tab) and selection all behave as in vanilla.

Reported by the user: the mod works in Lunar Client 26.3 (Fabric) on macOS. Which input method
and which checks were tried weren't recorded, so the table below still says **not yet**.

| Input method | OS | Windowed | Fullscreen | Run? |
|---|---|---|---|---|
| bada ime | macOS | | | **not yet** |
| Kotoeri (Japanese) | macOS | | | **not yet** |
| Pinyin (Simplified Chinese) | macOS / Windows | | | **not yet** |
| MS-IME Japanese / Korean | Windows | | | **not yet** |
| IBus or Fcitx | Linux (X11 and Wayland) | | | **not yet** |

Known limitation to confirm, not fix: with a Korean input method on macOS, an Enter that commits a
syllable doesn't also send (spec §4.3).
