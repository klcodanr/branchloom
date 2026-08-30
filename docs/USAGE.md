# Use Branchloom

## Sessions

To start a session:

1. Select a project and choose **New session**.
2. Enter a session name and prompt, then select an agent.
3. Branchloom creates a branch and worktree using the configured path template.
4. Project startup commands run in order inside the new worktree.
5. The selected agent starts in a terminal tab in that worktree.

Sessions can also be created by importing an existing branch, linked worktree, or
pull-request branch. Removing a session can preserve its worktree or delete it
after explicit confirmation.

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
`{number}`, `{title}`, `{url}`, `{branch}`, `{projectName}`, and `{worktreePath}`.
Values inserted into shell commands are quoted where required by the application.

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

## Application Data

The application stores data under `~/.branchloom`:

- `settings.json`: global settings, agents, editors, theme, and review prompt.
- `projects.json`: registered projects, sessions, and terminal tab metadata.
- `logs.jsonl`: recorded command, terminal, pull-request, and persistence problems.
- `settings.json.bak` and `projects.json.bak`: previous versions kept during saves.

Settings and projects are written atomically where supported. The application can
recover from the most recent JSON backup if a primary file cannot be read.
