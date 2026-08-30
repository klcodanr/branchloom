# Branchloom

A Java/Swing desktop for organizing Git repositories, isolated worktree sessions,
local agent terminals, and pull-request reviews in one workspace.

## Highlights

- Manage multiple Git repositories, branches, and linked worktrees.
- Create isolated agent sessions with project startup commands.
- Run agents and shell commands in native PTY-backed terminal tabs.
- Browse, import, and review GitHub pull requests.
- Configure agents, editors, themes, worktree paths, and review prompts.

Branchloom launches configured command-line programs; it does not provide or host
an agent model. Agents such as OpenCode and Claude are external tools.

## Documentation

- [Install Branchloom](docs/INSTALL.md)
- [Use Branchloom](docs/USAGE.md)
- [Develop and release Branchloom](docs/DEVELOPMENT.md)
