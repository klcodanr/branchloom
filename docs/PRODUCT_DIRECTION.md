# Product Direction

## Purpose

Branchloom should help a developer answer three questions quickly:

1. What was I working on?
2. What needs my attention?
3. What should I do next?

The application is not yet in broad use, so the initial experience should be
designed around this workflow rather than preserving the current collection of
views and tabs. Existing UI components are implementation material, not
constraints on the product structure.

## Navigation Model

The left navigation should expose the application's primary destinations
directly:

```text
Home
My Pull Requests
Review Queue
  [Queue] [Review Plan]

Projects
  Project A
    Session 1
    Session 2
  Project B
```

### Home

Home is the orientation and continuation view. It should not be a container
for unrelated PR tabs or a project catalog.

Home should provide:

- A clear continuation point for recent or active sessions.
- A small needs-attention section for failed sessions and other actionable
  problems.
- Quick actions such as creating a session, creating a terminal, and adding a
  project.
- An all-projects summary only as secondary information.

### My Pull Requests

`My Pull Requests` is a first-class global destination for pull requests
authored by the user across all projects.

### Review Queue

`Review Queue` is a first-class global destination for pull requests that need
the user's review across all projects.

It contains two tabs:

- `Queue`: the browsable and filterable review queue.
- `Review Plan`: a review-prioritization workspace that answers what should be
  reviewed first and why.

The summary must use only the review-request set. It must not combine authored
PRs or unrelated PRs from every project.

### Projects and Sessions

The project/session tree remains the persistent navigation for project work.
It should not also be responsible for global PR destinations. Project groups
and sessions should remain below the global destinations in the tree.

## Landing Page States

The landing page should adapt to the user's state instead of showing the same
project catalog for everyone.

### No Projects

Show a focused onboarding state:

- Primary action: `+ Add project`.
- Secondary action: import or open an existing project.
- Short explanation of the workflow: project, session, terminal or agent.
- No empty project-card grid.
- PR destinations may remain visible but should explain that there are no
  projects to load rather than showing empty dashboards.

### One Project

Avoid making the user choose a project when there is only one. Emphasize:

- Recent sessions.
- Active terminals or agents.
- `New session`.
- `New terminal`.
- `Open folder`.
- Relevant review requests as secondary attention items.

### Multiple Projects

Prioritize continuation and attention over catalog browsing:

1. Continue working.
2. Needs attention.
3. Quick actions.
4. All projects.

The all-projects section should be a compact list or table with project name,
path, recent activity, session count, and review-request count. Large cards
should not dominate the initial screen.

## Project Cards

Project cards are useful as an explicit project-management view, but they are
not the best default landing-page paradigm.

The current cards combine too many responsibilities:

- Project navigation.
- Session counts.
- Terminal status.
- Pull-request counts.
- Recent sessions.
- New session, terminal, and folder actions.

That makes them dense and duplicates information already available in the
left navigation. The existing `ProjectCards` work should therefore be reused
as one of the following rather than treated as the landing-page foundation:

- A compact all-projects list.
- An explicit project-management view.
- A project overview used after selecting a project.

The component should not be deleted until the replacement experience is in
place, but its current card-heavy layout should not dictate the new Home
structure.

## Review Queue Experience

The review-request workflow is not a generic reporting feature. Its job is to
help the user decide what to review and in what order.

### Queue Tab

The queue should make actionable state obvious. Use meaningful groups rather
than a flat list:

1. **Review now**
   - Review explicitly requested.
   - Not a draft.
   - Checks passing or complete.
   - No merge conflict.

2. **Review next**
   - Review requested but checks are pending.
   - Recently updated after review feedback.
   - Older outstanding requests that risk becoming stale.

3. **Waiting on author or CI**
   - Draft pull requests.
   - Changes requested without a subsequent update.
   - Failing checks.
   - Merge conflicts.

4. **Approved or ready to merge**
   - Pull requests that may no longer need another review but may need a merge
     decision.

The existing `PullRequestsBoard` grouping can provide a starting point, but
its current groups (`Not Ready`, `Waiting for Changes`, `Ready For Review`,
and `Approved`) should be revised to match this user workflow.

### Review Plan Tab

The summary should be a prioritized review plan, not a prose digest. It should
answer:

- Which pull request should I open first?
- Why is it higher priority than the others?
- What should I focus on while reviewing it?
- Is anything blocking a useful review?

Each recommendation should include:

- Project and pull-request identity.
- A clear rank or order.
- A short reason for the placement.
- Suggested review focus.
- Blockers or caveats.
- A direct action to open the pull request or project context.

Example:

```text
1. Project A #142 - Refresh authentication tokens
   Why: review requested, checks passing, and recently updated after feedback.
   Focus: session expiry, token refresh failure handling, and migration safety.
   Action: Open pull request
```

The summary should be useful even when the agent is unavailable. A
deterministic ordering and bucket explanation should render first; agent
generated rationale can enrich it when available.

### Initial Prioritization Signals

The current `PullRequest` model already provides enough information for a
first version:

- Review decision.
- Draft state.
- Checks status and counts.
- Mergeability.
- Created and updated timestamps.
- Author, title, and description.
- Comment summary.
- Project identity.

Use these signals to establish the initial buckets and ordering. Do not show a
false-precision numeric priority score when the application does not know the
user's business priorities.

Within the actionable group, prefer explainable signals such as:

- Explicit review request.
- Passing checks and merge readiness.
- Age of the outstanding request.
- Whether the pull request changed after feedback.
- Risk inferred from title, description, and comments.

### Future GitHub Data

For more defensible risk and impact ranking, consider adding:

- Labels.
- Changed-file count.
- Additions and deletions.
- Commit count.
- Review-requested users or teams.
- Review comments and reviewer state.

These fields should be added only when the first workflow demonstrates that
the current signals are insufficient.

## Proposed Technical Shape

The implementation should follow the existing view and service boundaries.

1. Add `MY_PR` and `REVIEW_REQUESTS` destinations to `ViewId`.
2. Add root navigation nodes for `Home`, `My Pull Requests`, and `Review Queue`.
3. Update the tree synchronizer so project groups begin after the complete
   global-navigation section rather than assuming `Home` is the only reserved
   node.
4. Route root-node selection through `ViewCoordinator`.
5. Extract the all-project authored-PR board into the `My Pull Requests` view.
6. Create a `Review Queue` view containing the queue and `Review Plan` tabs.
7. Move the current summary preparation into the review-request view and
   restrict its input to review requests.
8. Remove the PR tabs from `HomeView`.
9. Restructure `HomeView` around onboarding, continuation, attention, and
   quick actions.
10. Reuse `ProjectCards` as a compact all-projects view or project-management
    view after the new Home flow is established.

Prefer direct concrete views and callbacks over a generic navigation registry.
The application has only a small number of known top-level destinations, so
an enum and direct switch are sufficient.

## Acceptance Criteria

- A new user immediately knows how to add their first project.
- A returning user can resume recent work without scanning every project.
- Global authored PRs and the review queue are visible in the left navigation.
- The review queue is not hidden inside Home.
- Review Plan contains only review requests.
- Review Plan explains what to review first and why.
- Blocked and waiting pull requests are visually distinct from actionable
  reviews.
- Project/session navigation remains available without duplicating global PR
  navigation.
- The landing page does not depend on large project cards to communicate the
  primary workflow.
