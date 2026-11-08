# RichTextCraft

A client-only Fabric mod for Minecraft 26.3 that makes OS input methods (Japanese, Chinese,
Korean and any other) behave in every text field as they do in a native app:

- **Marked text inline.** The text being composed is drawn at the caret, underlined, with the
  converting clause underlined thick. It isn't part of the field's value until committed.
- **Candidate window in place.** The OS candidate window sits under the text being converted, in
  windowed and fullscreen mode.
- **Keys belong to the input method while it composes.** Enter confirms without sending chat,
  and Escape cancels without closing the screen.
- **Focus loss commits.** Clicking Done, another field or anywhere else keeps what you were
  typing.

It covers chat and commands, the anvil, searches, command and structure blocks, book pages, signs
and every other `EditBox` or `MultiLineEditBox`. RichTextCraft has no input method of its own.

Requires Minecraft 26.3, Fabric Loader 0.19.3+, Fabric API and Java 25.

## Build and test

```sh
./gradlew build               # mod jar in build/libs
./gradlew test                # unit tests
./gradlew runClientGameTest   # in-game tests
```

[docs/spec.md](docs/spec.md) is the specification and [docs/testing.md](docs/testing.md) the test
plan.

## Licence

The code is dedicated to the public domain under [CC0 1.0](LICENSE) by Mirae Research.

Files from other projects keep their own licences: the Gradle wrapper (`gradlew`, `gradlew.bat`
and `gradle/wrapper/`) is under the Apache License 2.0. Minecraft, Fabric and their libraries,
which the mod builds against but doesn't include, are under their own terms.
