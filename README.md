# Branchloom

A Java/Swing desktop for organizing Git repositories, isolated worktree sessions,
local agent terminals, and pull-request reviews in one workspace.

## Capabilities

### Projects And Worktrees

- Register multiple Git repositories and organize them into project groups.
- Configure project-specific GitHub accounts, worktree paths, and startup commands.
- Import existing local branches, linked worktrees, and GitHub pull-request branches as sessions.
- Open project and worktree directories in the system file manager.

### Agent Sessions

- Create agent sessions from a name, prompt, and configured agent command.
- Create an isolated Git worktree and branch for each new session.
- Run project startup commands with setup progress, output, retry, and cancel actions.
- Preserve or delete a session's worktree when removing the session.
- Show session summaries, terminal status, and Git working-tree status.

### Terminals And Tools

- Run agents and ordinary shell commands in native PTY-backed terminal tabs.
- Rename, close, and switch between terminal tabs with keyboard shortcuts.
- Configure external editor commands and launch them from a project or session.
- Search projects, sessions, and terminals from the keyboard.

### Pull Requests

- Browse authored pull requests and review requests in refreshable, searchable boards.
- Filter pull requests by number, title, author, or branch.
- Group pull requests by draft/readiness, requested changes, readiness, and approval.
- Open pull requests in a browser or import their branches as sessions.
- Launch an agent review using a configurable review prompt.

### Application Experience

- Search pull requests from the keyboard.
- Switch between system, light, and dark appearance themes.
- Record application and command failures in a Problems view with persistent logs.

Branchloom runs configured commands through the platform shell. It does not
provide or host an agent model; agents such as OpenCode and Claude are external
command-line programs launched inside the session worktree.

## Requirements

- Java 25. Gradle's toolchain support can provision it automatically.
- Git.
- A shell available on the host system.
- Optional: [GitHub CLI](https://cli.github.com/) (`gh`) for pull-request views,
  pull-request import, and GitHub account selection.
- Optional: an installed agent CLI such as `opencode` or `claude`.
- Optional: an installed editor CLI such as `code` or `cursor`.

For GitHub features, authenticate first:

```bash
gh auth login
```

The project must have a GitHub remote. Multiple authenticated GitHub hosts and
users can be selected per project; requests are loaded with the selected account.

## Run

Requires Gradle. The build uses Java 25 and automatically provisions a matching
toolchain when it is not installed locally. With Gradle installed:

```bash
gradle run
```

On first launch, add a Git repository from the project tree. Available command
line tools are detected and added as default agents or editors when possible.

## Sessions

To start a session:

1. Select a project and choose **New session**.
2. Enter a session name and prompt, then select an agent.
3. Branchloom creates a branch and worktree using the configured path template.
4. Project startup commands run in order inside the new worktree.
5. The selected agent starts in a terminal tab in that worktree.

Sessions can also be created by importing an existing branch, linked worktree,
or pull-request branch. Removing a session can preserve its worktree or delete
it after an explicit confirmation.

Each session provides a summary, status indicators, closable and renameable
terminal tabs, configured agent/editor actions, copy-path, and open-in-file-manager
actions. Terminal processes are real interactive PTYs, so interactive CLI tools
and color output are supported.

## Pull Requests

Each project has **My PRs** and **Review requests** views. Pull requests can be:

- Filtered by number, title, author, or branch.
- Grouped by draft/readiness, requested changes, readiness, and approval.
- Opened in the system browser.
- Imported into a new session through a fetched pull-request branch.
- Reviewed by an available configured agent using the configured review prompt.

The default review prompt supports `{number}`, `{title}`, `{url}`, `{branch}`,
`{projectName}`, and `{worktreePath}`. Pull-request data is retrieved with `gh`
and requires an accessible GitHub remote and authentication.

## Configuration

Open **Settings** to configure global defaults:

- Default worktree path template.
- Global startup commands, one command or script path per line.
- Appearance theme.
- Agent names, new-session commands, and open commands.
- External editor names and commands.
- Pull-request review prompt.

Project settings can override the worktree path, startup commands, project group,
and GitHub CLI account. A blank project override uses the global default.

Worktree and startup command templates support:

`{projectName}`, `{projectPath}`, `{sessionName}`, `{sessionSlug}`, and
`{worktreePath}`.

Agent commands support those variables plus `{prompt}`. Review prompts support
`{number}`, `{title}`, `{url}`, `{branch}`, `{projectName}`, and
`{worktreePath}`. Values inserted into shell commands are quoted where required
by the application.

## Keyboard Shortcuts

The shortcuts use `Cmd` on macOS and the platform menu shortcut elsewhere:

| Shortcut | Action |
| --- | --- |
| `Cmd/Ctrl+F` | Find a project, session, terminal, or PR search |
| `Cmd/Ctrl+K` | Open the command palette |
| `Cmd/Ctrl+N` | Create a session |
| `Cmd/Ctrl+Shift+N` | Add a project |
| `Cmd/Ctrl+T` | Open a terminal |
| `Cmd/Ctrl+W` | Close the active terminal |
| `Cmd/Ctrl+Shift+R` | Rename the active terminal |
| `Cmd/Ctrl+1` through `9` | Select a terminal |
| `Escape` | Clear transient focus and menus |

## Package

```bash
gradle jpackage
```

This creates an unsigned platform-native application image with a bundled Java
runtime in `build/jpackage`. To create an installer, run:

```bash
gradle jpackageInstaller
```

The installer format is selected for the current OS: DMG on macOS, MSI on
Windows, and DEB on Linux. `jpackage` must be available in the Java 25
toolchain. Release builds should additionally be signed and notarized using the
platform's signing tools and credentials.

For macOS signing, set `BRANCHLOOM_MAC_SIGNING_KEY` to the Developer ID
Application identity and optionally set `BRANCHLOOM_MAC_SIGNING_KEYCHAIN`. For
Windows signing, set `BRANCHLOOM_WIN_SIGNING_KEYSTORE` and optionally set
`BRANCHLOOM_WIN_SIGNING_KEY_ALIAS` and `BRANCHLOOM_WIN_SIGNING_KEYSTORE_TYPE`.

Platform-native `.icns`, `.ico`, and `.png` icons can be added to the packaging
task when available; the current build uses the default `jpackage` icon because
the source asset is SVG.

For development, run `gradle dev`. It watches `src/main` and recompiles and
restarts the app when source files change.

`gradle check` runs formatting, unused-code checks with PMD, and SpotBugs
analysis. `gradle spotlessApply` formats Java sources. Reports are written
under `build/reports/`; intentional Swing/framework findings are documented in
`config/spotbugs/exclude.xml`.

## Releases

Every push to `main` creates a release automatically. The workflow creates a
UTC timestamp tag and GitHub Release, generates release notes, and attaches one
native installer for each platform:

- macOS: DMG
- Windows: MSI
- Linux: DEB

Release tags use the UTC `vYYYYMMDD.HHMMSS` format, for example
`v20260819.143012`. The timestamp is passed to Gradle as `-PreleaseVersion`, so
the application, JAR, and `jpackage` metadata use the timestamp without the
leading `v`. The generated installers are unsigned unless signing credentials
and the corresponding packaging configuration are added to the workflow.

The macOS DMG is also published to the `klcodanr/homebrew-tap` repository as a
Homebrew cask. The `HOMEBREW_TAP_TOKEN` repository secret must be a GitHub token
with write access to that tap. Once published, install it with:

```bash
brew tap klcodanr/tap
brew install --cask branchloom
```

Gradle does not generate release timestamps locally. The Git tag is the source
of truth, while local builds default to version `1.0.0` and can override it
explicitly:

```bash
gradle -PreleaseVersion=20260819.143012 jpackageInstaller
```

## Application Data

The application stores data under `~/.branchloom`:

- `settings.json`: global settings, agents, editors, theme, and review prompt.
- `projects.json`: registered projects, sessions, and terminal tab metadata.
- `logs.jsonl`: recorded command, terminal, pull-request, and persistence problems.
- `settings.json.bak` and `projects.json.bak`: previous versions kept during saves.

Settings and projects are written atomically where supported. The application can
recover from the most recent JSON backup if a primary file cannot be read.

## Code style

Java formatting is enforced with Spotless and Google Java Format:

```bash
gradle spotlessApply   # format source
gradle check           # verify formatting
```

Use four spaces for Java indentation, LF line endings, UTF-8 source files, and a
final newline. Keep domain models, persistence, command execution, and Swing
presentation in separate classes.
