# Install Branchloom

## Homebrew

On macOS, install the latest published release with Homebrew:

```bash
brew tap klcodanr/tap
brew install --cask branchloom
```

The macOS build is currently unsigned. If macOS blocks the first launch, open
**System Settings > Privacy & Security**, scroll to the security message for
Branchloom, and click **Open Anyway**. If that option does not appear, remove
the quarantine flag and open the app from Terminal:

```bash
xattr -dr com.apple.quarantine /Applications/Branchloom.app
open /Applications/Branchloom.app
```

## Requirements

Branchloom requires:

- Java 25. Gradle's toolchain support can provision it automatically for local builds.
- Git.
- A shell available on the host system.

Optional integrations include:

- [GitHub CLI](https://cli.github.com/) (`gh`) for pull-request views, pull-request
  import, and GitHub account selection.
- An installed agent CLI such as `opencode` or `claude`.
- An installed editor CLI such as `code` or `cursor`.

Authenticate the GitHub CLI before using GitHub features:

```bash
gh auth login
```

The project must have a GitHub remote. Multiple authenticated GitHub hosts and
users can be selected per project.

## Run From Source

With Gradle installed, start the application with:

```bash
gradle run
```

On first launch, add a Git repository from the project tree. Available command-line
tools are detected and added as default agents or editors when possible.
