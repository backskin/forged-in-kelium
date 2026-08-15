# macOS / Linux launchers

Honest scope: these are **not** native app bundles like the Windows `.exe`
files in `dist/`. `jpackage` can only package for the OS it runs on, so this
Windows machine can't cross-build a macOS `.app` or a Linux AppImage — and
there's no macOS/Linux machine here to build or test one on anyway.

What these scripts actually do: run the app straight from source via Maven,
the same way `mvn exec:java` is already documented to work in the main
`README.md`. The Java/Swing code itself is cross-platform; only the exe
packaging pipeline (`make-exe.ps1`) is Windows-only.

## Requirements on the target machine

- **JDK 21** (`java -version` should say 21).
- **Maven 3.9+** (`mvn -version`).
- Internet access on the *first* run only — Maven downloads dependencies into
  `~/.m2` and reuses them offline after that.

## Running

```bash
./replay2-macos-linux.sh       # KeliumReplay2 — game review
./constructor-macos-linux.sh   # KeliumConstructor — layout editor
./runner-macos-linux.sh        # KeliumRunner — batch simulations
./help-macos-linux.sh          # KeliumHelp — rulebook/card reference
```

If a script won't run ("Permission denied"), mark it executable first:

```bash
chmod +x *.sh
```

Game data (rules, cards, layouts) is found automatically at
`../simulator/data` relative to `java-sim/` — same as on Windows, no setup
needed as long as this folder stays inside the project tree.

## If you actually want a native macOS/Linux app later

That needs to be built (and tested) *on* a macOS or Linux machine, using the
same `jlink` + `jpackage` approach as `make-exe.ps1`, adapted for that OS's
packaging format (`.app`/`.dmg` on macOS, AppImage/`.deb` on Linux). Ask on
that machine and it can be done properly there.
