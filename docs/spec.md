# RichTextCraft: specification

RichTextCraft is a client-only Fabric mod for Minecraft 26.3. It lets any OS input method (macOS
bada ime, Kotoeri, Pinyin, Windows MS-IME, Linux IBus/Fcitx) type properly in every text widget.
It has no input method of its own. It is the text-input layer that OS input methods plug into.

This document is the source of truth. If the code and this document disagree, fix both in the same
change.

## 0. What the game and SDL already do

These facts come from the 26.3 client code and from SDL 3.4.14, the version LWJGL 3.4.3 bundles.
The SDL code was read at tag `release-3.4.14`.

- **Text input.** `com.mojang.blaze3d.platform.TextInputManager` wraps `SDL_StartTextInput`,
  `SDL_StopTextInput` and `SDL_SetTextInputArea`. Its owner is the focused text widget, set by
  `Minecraft.onTextInputFocusChange` when an `EditBox` or `MultiLineEditBox` gains or loses focus.
  The sign editor makes itself the owner in `init`. `Gui.setScreen` clears focus and then calls
  `stopTextInput()` before the old screen's `removed()`.
- **Marked text** (SDL: "editing text", the game: "preedit"). `RenderSystem` sets
  `SDL_IME_IMPLEMENTED_UI=composition`, so on every platform SDL sends the input method's marked
  text as `SDL_EVENT_TEXT_EDITING` (text, start, length). The OS still draws the candidate window.
  `SDLEventHandler` turns the event into a `PreeditEvent`, and `KeyboardHandler.textEditing`
  passes it to the screen's `preeditUpdated`. A commit arrives as `SDL_EVENT_TEXT_INPUT`:
  `KeyboardHandler.textInput` clears the preedit and then sends one `charTyped` per code point.
- **Vanilla drawing.** `EditBox`, `MultiLineEditBox` and the sign editor show marked text in a
  pop-up bubble (`IMEPreeditOverlay`) under or above the line, not inline.
- **Keys the input method consumes** never reach the game on macOS (SDL's pending-key
  mechanism), Windows (`VK_PROCESSKEY` is trapped), X11 (`XFilterEvent`) or Wayland with
  text-input-v3. On Wayland without text-input-v3 (IBus/Fcitx over D-Bus), SDL sends every key
  down, even ones the input method handled.
- **On macOS, a key that commits marked text produces no key event**: SDL sends the empty edit and
  then the text input, but drops the key. When a Korean input method commits a syllable on Enter
  and expects the Enter to act as well (send the chat message), SDL loses the Enter. It looks
  exactly like a Japanese Enter that only confirms, so the mod can't tell them apart (§4.3).
- **`SDL_StopTextInput`** cancels the composition on Windows (`CPS_CANCEL`) and on X11
  (`Xutf8ResetIC`, and the text it returns is freed). On macOS it removes SDL's field editor; what
  the input method does then is up to the input method. On Wayland it is up to the compositor.
- **`SDL_ClearComposition`** cancels on Windows and does nothing on macOS, X11 and Wayland.
- **`SDL_SetTextInputArea(rect, cursor)`**: the rectangle is in window points. Windows puts the
  candidate window at `rect.x + cursor` and keeps it out of the rectangle. X11 puts it at
  `(rect.x + cursor, rect.y + rect.h)`. Wayland uses `min(rect.x + cursor, rect.x + rect.w)`.
  macOS uses the rectangle only. The game always passes `cursor = -1`.
- **No candidate list events.** `SDL_EVENT_TEXT_EDITING_CANDIDATES` is never sent without
  `candidates` in the hint, and the mod doesn't ask for it: the OS draws candidates.

## 1. Widgets

Every text widget in 26.3 is one of three kinds. The mod covers all three.

| Kind | Where in the game |
|---|---|
| `EditBox` (single line) | chat and commands (`ChatScreen`); anvil rename; creative inventory search; recipe book search; command block (command and previous output); structure block, jigsaw and test block fields; book title (`BookSignScreen`); world creation, editing and selection search; Add Server and Direct Connect; flat-world presets and buffet world; game rules search; language, pack and social interaction search; abuse report comments; friends; debug options search; dialog text inputs |
| `MultiLineEditBox` | book pages (`BookEditScreen`); multi-line dialog inputs (`InputControlHandlers`); report comments |
| Sign editor (`AbstractSignEditScreen`, through `TextFieldHelper`) | signs and hanging signs |

The mod finds widgets by type, not by screen. Screens from other mods that use these widgets are
covered too. Widgets of other types keep vanilla behaviour, the pop-up bubble included.

## 2. Marked text

1. **Not part of the value.** The marked text is never in the widget's value. Listeners (chat
   suggestions, anvil rename packets, sign updates) see no change until the commit.
2. **Drawn inline at the caret.** While composing, the widget draws its value with the marked text
   in place of the selection, at the caret. The whole marked text gets a 1-pixel underline in the
   text colour. The segment being converted, if SDL reports one (a positive length), gets a
   2-pixel underline, inset by a pixel at each end so neighbouring clauses read apart. The
   widget's caret is drawn as a bar at the input method's caret inside the marked text, even at
   the end of the text, where vanilla would draw an underscore. The selection
   highlight and chat's suggestion ghost text are hidden while composing.
3. **Scrolling and wrapping.** A single-line field scrolls so the marked text, and the caret
   inside it, are in view. A multi-line field wraps the marked text like normal text and scrolls
   to the caret. On a sign, the marked text is drawn on the current line, centred with the rest
   of the line.
4. **Commit.** A text-input event ends the composition. The game then inserts the committed text at
   the caret, replacing any selection, through the widget's normal typing path. Length limits,
   character filters and listeners apply as for typed text.
5. **Cancel.** An empty edit event ends the composition and changes nothing.
6. **No pop-up.** For the three kinds in §1, the vanilla pop-up bubble is off.
7. **Formatting.** Chat's command colouring still applies to the text before and after the marked
   text, at its true offsets. The marked text itself is plain.

## 3. Candidate window placement

While a widget owns text input, the mod reports a text input area every frame. The game only
calls SDL when the area changes.

- **Not composing:** a 1-pixel-wide area at the caret, one line tall (vanilla).
- **Composing:** a 1-pixel-wide area, one line tall, at the start of the converting segment (or
  of the marked text, if there is no segment). Every platform then puts the candidate window
  under the text being converted, which is where native fields put it. On signs the area stays
  at the caret inside the marked text: the sign is drawn scaled, and vanilla already works out
  the caret's screen position.
- Coordinates are GUI coordinates. The game converts them using the GUI scale and pixel density,
  so the area is correct in windowed and fullscreen mode, at any GUI scale and on Retina displays.
  A window resize or a GUI scale change moves the area on the next frame.
- **Multi-line fields:** the area takes the scroll offset into account. Vanilla doesn't, so its
  candidate window drifts down when a book page is scrolled.

## 4. Keys while composing

1. **Held back.** While a widget in §1 is composing, every key press and repeat belongs to the
   input method and doesn't reach the game. That covers Enter, Backspace, Delete, the arrows,
   Home, End, Tab, Escape, Space, letters and shortcuts. Enter doesn't send chat, and Escape
   doesn't close the screen. On most platforms SDL already keeps these keys back (§0). This rule
   covers the platforms where it doesn't (Wayland without text-input-v3) and any key that
   slips through.
2. **Passed through:** key releases (so no key is ever stuck down), F1–F24 (screenshots, debug,
   fullscreen), and lone modifiers and Caps Lock.
3. **Korean Enter on macOS: a known limitation.** When an input method commits a syllable with
   Enter, SDL doesn't deliver the Enter key (§0). The first Enter commits and a second one sends.
   Japanese and Chinese input methods behave as expected (Enter confirms only).
4. **Not composing:** every key behaves exactly as in vanilla.

## 5. Focus loss: commit

Decided by the user: **the marked text is committed**, as in native macOS and Windows fields.

1. **When it applies:** any time the composing widget stops owning text input. That covers a click
   on another widget, Tab, Done, the screen closing for any reason (Escape when not composing, the
   game closing it, disconnecting) and the game's `stopTextInput`.
2. **What happens:** the mod inserts the marked text into the widget, through the same path as an
   input method commit (§2.4). This happens before the widget loses focus and before the screen's
   `removed()`, so a sign update or Done reads the committed text.
3. **Clicks commit first.** A mouse click anywhere on the screen while composing commits before
   the click reaches a widget, even a click inside the composing field. Vanilla runs a button's
   action before it moves focus, so without this, Done would read the field without the marked
   text. A click inside the field then moves the caret as usual.
4. **The input method forgets the text.** After the commit, the mod makes the input method drop
   its composition, so the text isn't inserted again:
   - Windows: `SDL_ClearComposition` (cancel).
   - macOS: `-[NSTextInputContext discardMarkedText]` on the current input context.
   - Linux: stop and restart SDL text input (an IME reset).

   As a safety net, if the next text input after a focus-loss commit is the same text, and no key
   was pressed in between, the mod drops it.

## 6. No regressions

- Players without an input method: typing, shortcuts, selection, clipboard and chat suggestions are
  unchanged. Every hook does nothing unless a composition is active, except the text-input-area
  correction for scrolled multi-line fields (§3).
- Widgets of other types (§1) keep vanilla behaviour.

## 7. Not in scope

An input method of its own, anything server-side, and key remapping.
