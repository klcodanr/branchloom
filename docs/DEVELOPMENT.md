# Develop Branchloom

## Build And Test

The build uses Java 25 and automatically provisions a matching toolchain when it
is not installed locally.

Run the automated tests with:

```bash
gradle test
```

For development, run:

```bash
gradle dev
```

This watches `src/main`, recompiles, and restarts the app when source files change.

`gradle check` runs formatting, unused-code checks with PMD, and SpotBugs analysis.
Reports are written under `build/reports/`; intentional Swing/framework findings
are documented in `config/spotbugs/exclude.xml`.

## Package Locally

Create an unsigned platform-native application image with a bundled Java runtime:

```bash
gradle jpackage
```

The output is written to `build/jpackage`. To create an installer, run:

```bash
gradle jpackageInstaller
```

The installer format is selected for the current OS: DMG on macOS, MSI on Windows,
and DEB on Linux. `jpackage` must be available in the Java 25 toolchain.

## Releases

Every push to `main` creates a release automatically. The workflow creates a UTC
timestamp tag and GitHub Release, generates release notes, and attaches native
installers for macOS and Linux:

- macOS: DMG
- Linux: DEB

Release tags use the UTC `vYYYYMMDD.HHMMSS` format, for example
`v20260819.143012`. The timestamp is passed to Gradle as `-PreleaseVersion`, so
application and packaging metadata use the timestamp without the leading `v`.

The macOS DMG is also published to the `klcodanr/homebrew-tap` repository as the
`branchloom` Homebrew cask. The `HOMEBREW_TAP_TOKEN` repository secret must be a
GitHub token with write access to that tap.

Gradle does not generate release timestamps locally. Git tags are the source of
truth, while local builds default to version `1.0.0` and can override it:

```bash
gradle -PreleaseVersion=20260819.143012 jpackageInstaller
```

Release builds should be signed and notarized using the platform's signing tools
and credentials. The workflow supports these environment variables:

- macOS: `BRANCHLOOM_MAC_SIGNING_KEY` and optionally `BRANCHLOOM_MAC_SIGNING_KEYCHAIN`.
- Windows: `BRANCHLOOM_WIN_SIGNING_KEYSTORE`, optionally
  `BRANCHLOOM_WIN_SIGNING_KEY_ALIAS` and `BRANCHLOOM_WIN_SIGNING_KEYSTORE_TYPE`.

## Code Style

Java formatting is enforced with Spotless and Google Java Format:

```bash
gradle spotlessApply   # format source
gradle check           # verify formatting
```

Use four spaces for Java indentation, LF line endings, UTF-8 source files, and a
final newline. Keep domain models, persistence, command execution, and Swing
presentation in separate classes.
