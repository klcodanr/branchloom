# Use Branchloom

## Sessions

To start a session:

1. Select a project and choose **New session**.
2. Enter a session name and prompt, then select an agent.
3. Branchloom creates a branch and worktree using the configured path template.
4. Project startup command files or commands run in order inside the new worktree.
5. The selected agent starts in a terminal tab in that worktree.

Clipboard images can be pasted into an embedded terminal using any of the terminal's paste actions,
including the platform paste shortcut, context menu, and selection paste. Branchloom writes the image
to a temporary PNG file and pastes that file's absolute path into the terminal, so shells and agents can
read the image using their normal file handling. Text paste behavior is unchanged.
Dragging a file onto the terminal inserts that file's absolute path without copying the file.

Sessions can also be created by importing an existing branch, linked worktree, or
pull-request branch. Removing a session can preserve its worktree or delete it
after explicit confirmation.

Each session provides a summary, status indicators, closable and renameable
terminal tabs, configured agent/editor actions, copy-path, and open-in-file-manager
actions. Terminal processes are real interactive PTYs, so interactive CLI tools
and color output are supported.

## Pull Requests

Branchloom provides a global **Pull Requests** view and a project-level **Pull
Requests** tab. Each view can use the configured pull-request filters. The
default filters are **My PRs**, **Reviewable**, and **My Reviews**. Pull requests
can be:

- Filtered with a configured GitHub search query and searched locally by number,
  title, author, description, or branch.
- Grouped by draft/readiness, requested changes, readiness, and approval.
- Opened in the system browser.
- Imported into a new session through a fetched pull-request branch.
- Reviewed by an available configured agent using the configured review prompt.

The default review prompt supports `{number}`, `{title}`, `{url}`, `{branch}`,
`{projectName}`, and `{worktreePath}`. Pull-request data is retrieved with `gh`
and requires an accessible GitHub remote and authentication.

## Configuration

Open **Settings** to open the global `settings.json` file in the first configured
external editor. Newly initialized settings include a `$schema` reference to the
local `settings.schema.json` file for editor completion and validation. Use
**Refresh Settings** after editing the file to reload valid changes and apply the
selected theme.

On first launch, Branchloom creates the settings file with detected agents and
editors plus the defaults below. It does not rewrite an existing settings file
automatically. If the JSON is invalid, Branchloom leaves it unchanged, uses
in-memory defaults, and reports the settings path and the refresh action needed
after the file is repaired.

Global settings configure:

- Default worktree path template.
- Appearance theme.
- Agent names, new-session commands, and open commands.
- External editor names and commands.
- Pull-request review prompt.
- Jira instance endpoint.
- Named kanban boards, source queries, columns, and JEXL rules.

Project settings configure the repository path, startup command files or commands
(one command or script path per line), agent context, project group, and GitHub
connection. They can override the worktree path and use a blank worktree override
to keep the global default.

Worktree and startup command templates support:

`{projectName}`, `{projectPath}`, `{sessionName}`, `{sessionSlug}`, and
`{worktreePath}`.

Agent commands support those variables plus `{prompt}`. Review prompts support
`{number}`, `{title}`, `{url}`, `{branch}`, `{projectName}`, and `{worktreePath}`.
Values inserted into shell commands are quoted where required by the application.

## Board Rules

Board columns use Apache Commons JEXL expressions to decide whether an item is
shown in that column. The expression is evaluated against the board item's data.
The common board-item properties are available as top-level names. Each source
can expose additional properties on its concrete board-item type, and those
properties are available to JEXL as well.

For example, these rules can be used for common board fields:

```jexl
state == 'OPEN'
project.name == 'Branchloom'
title =~ 'authentication'
```

The following examples show the public properties available to board rules.
They are representative JEXL contexts, not persisted JSON. Internal fields may
exist on the underlying objects but are not part of the board-rule contract.

### Enumerated Values

The `source` field has these values:

| Value | Meaning |
| --- | --- |
| `PULL_REQUEST` | GitHub pull request |
| `GITHUB_ISSUES` | GitHub issue |
| `JIRA` | Jira issue |
| `AGENT_SESSION` | Branchloom agent session |

Pull-request `state` has these values in the complete pull-request object:

| Value | Meaning |
| --- | --- |
| `OPEN` | Pull request is open |
| `CLOSED` | Pull request is closed without being merged |
| `MERGED` | Pull request has been merged |

Pull-request detail `status` has these values:

| Value | Meaning |
| --- | --- |
| `READY` | The pull request is mergeable and checks are not failing |
| `CONFLICTED` | The pull request has merge conflicts |
| `DRAFT` | The pull request is a draft |
| `BLOCKED` | The pull request is blocked from merging |
| `PENDING` | GitHub has not finished determining merge readiness |
| `CHECKS_FAILING` | One or more checks are failing |
| `OTHER` | No specific status condition matched |

Pull-request check `checksStatus` has these values:

| Value | Meaning |
| --- | --- |
| `UNKNOWN` | No checks are available |
| `FAILING` | At least one check is failing |
| `PENDING` | At least one check has not completed |
| `PASSING` | All available checks are passing |

Individual pull-request checks expose `status` values:

| Value | Meaning |
| --- | --- |
| `QUEUED` | Check is waiting to run |
| `IN_PROGRESS` | Check is currently running |
| `COMPLETED` | Check has completed |
| `UNKNOWN` | GitHub returned an unrecognized check state |

Completed checks expose `conclusion` values:

| Value | Meaning |
| --- | --- |
| `ACTION_REQUIRED` | Further action is required |
| `CANCELLED` | Check was cancelled |
| `FAILURE` | Check failed |
| `NEUTRAL` | Check completed without a pass or failure |
| `SUCCESS` | Check passed |
| `SKIPPED` | Check was skipped |
| `STALE` | Check result is stale |
| `TIMED_OUT` | Check exceeded its time limit |
| `UNKNOWN` | GitHub returned an unrecognized conclusion |

The pull-request `state` value is an enum, so rules should use its enum name:

```jexl
state == 'OPEN'
```

GitHub issue `state` has these values:

| Value | Meaning |
| --- | --- |
| `open` | Issue is open |
| `closed` | Issue is closed |

Agent-session `state` is normalized by Branchloom and has these values:

| Value | Meaning |
| --- | --- |
| `idle` | The session has no active terminal |
| `active` | The session has at least one active terminal |

Jira `status` values are supplied by the Jira instance and are not a fixed
Branchloom enumeration.

### Pull Request Details

Pull-request rules evaluate the complete `PullRequestDetails` object. It
contains all inherited `PullRequest` properties plus the detail-specific
properties loaded from GitHub:

```json
{
  "source": "PULL_REQUEST",
  "id": "project-uuid:42",
  "title": "Fix authentication flow",
  "description": "Refreshes the login token before retrying.",
  "url": "https://github.example.com/acme/branchloom/pull/42",
  "boardProjectId": "project-uuid",
  "project": {
    "name": "Branchloom",
    "path": "/work/branchloom",
    "group": "Adobe"
  },
  "number": 42,
  "state": "OPEN",
  "url": "https://github.example.com/acme/branchloom/pull/42",
  "createdAt": "date value",
  "updatedAt": "date value",
  "author": {
    "login": "octocat",
    "url": "https://github.example.com/octocat"
  },
  "headBranch": "fix-authentication",
  "headSha": "0123456789abcdef",
  "baseBranch": "main",
  "draft": false,
  "mergeable": true,
  "mergableState": "clean",
  "additions": 24,
  "deletions": 8,
  "changedFiles": 3,
  "status": "READY",
  "checks": {
    "checksStatus": "PASSING",
    "checks": []
  }
}
```

Useful pull-request rules include:

```jexl
state == 'OPEN'
project.name == 'Branchloom'
number == 42
headBranch =~ '^fix-'
```

The inherited `state`, `number`, and other common values come from
`PullRequest`. The `draft`, `mergeable`, `mergableState`, `additions`,
`deletions`, `changedFiles`, `checks`, and `status` values come from
`PullRequestDetails`. The linked `project`, `author`, and `checks` values are
objects, so rules can address nested properties such as `project.name`.

### GitHub Issue

GitHub issues are represented by a source-specific board item:

```json
{
  "source": "GITHUB_ISSUES",
  "id": "project-uuid:17",
  "title": "Add board filtering",
  "description": "Support filtering cards by source fields.",
  "url": "https://github.example.com/acme/branchloom/issues/17",
  "boardProjectId": "project-uuid",
  "state": "open",
  "number": 17,
  "project": "Branchloom"
}
```

Useful GitHub issue rules include:

```jexl
state == 'open'
project == 'Branchloom'
number == '17'
title =~ 'board'
```

### Agent Session

Agent sessions are represented by a source-specific board item:

```json
{
  "source": "AGENT_SESSION",
  "id": "session-uuid",
  "title": "Implement board filters",
  "description": "Add JEXL rules for board columns.",
  "url": null,
  "boardProjectId": "project-uuid",
  "state": "active",
  "agent": "Claude",
  "project": "Branchloom",
  "branch": "/work/branchloom/.worktrees/board-filters"
}
```

Useful session rules include:

```jexl
state == 'active'
agent == 'Claude'
project == 'Branchloom'
branch =~ 'worktrees'
```

### Jira Issue

Jira issues are loaded when the board source is enabled, `jiraEndpoint` is set in
`settings.json`, and the `JIRA_TOKEN` environment variable contains a bearer
token. The loader queries Jira's REST API and represents each issue as a basic
board item:

```json
{
  "source": "JIRA",
  "id": "PROJ-123",
  "title": "Add board filtering",
  "description": "Support filtering cards by source fields.",
  "url": "https://jira.example.com/browse/PROJ-123",
  "createdAt": "date value",
  "updatedAt": "date value",
  "boardProjectId": "PROJ",
  "status": "In Progress",
  "author": "reporter@example.com",
  "assignee": "owner@example.com"
}
```

Jira `createdAt` and `updatedAt` come from Jira's `created` and `updated` fields.
`author` is the creator's email address and `assignee` is the assignee's email
address. They are separate fields and may be blank when Jira does not provide an
email address or an assignee. The Jira `status` field is the status name supplied
by Jira.

Useful Jira rules include:

```jexl
status == 'In Progress'
author == 'reporter@example.com'
assignee == 'owner@example.com'
```

Rules for a source only apply when that source has a rule configured for the
column. A missing rule for an item's source excludes the item from that
column. A blank rule matches every item from that source.

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

The application stores data under `~/.branchloom` by default. Tests and alternate
launchers may provide a different data directory:

- `settings.json`: global settings, agents, editors, theme, review prompt, Jira
  endpoint, and board definitions.
- `settings.schema.json`: local JSON Schema used by the settings editor.
- `projects.json`: registered projects, sessions, and terminal tab metadata.
- `branchloom.log`: application events and errors in JSON-lines format.
- `terminal-history/`: history files for retained terminal sessions.

Project/session data is persisted separately in `projects.json`. Missing settings
are initialized once; later settings edits are made externally and loaded with
**Refresh Settings**. If a settings file cannot be read, the application logs the
problem, leaves the file untouched, and uses in-memory defaults until it is fixed
and refreshed. Invalid project data falls back independently without discarding
valid settings.
